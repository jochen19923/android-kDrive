/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.ui.menu.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.BaseActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.activateAutoSync
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.activity_sync_settings.*
import kotlinx.android.synthetic.main.view_date_input.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SyncSettingsActivity : BaseActivity() {

    private val syncSettingsViewModel: SyncSettingsViewModel by viewModels()
    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private var oldSyncSettings: SyncSettings? = null
    private var editNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_settings)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val permission = DrivePermissions()
        permission.registerPermissions(this)

        activateSyncSwitch.isChecked = AccountUtils.isEnableAppSync()
        saveSettingVisibility(activateSyncSwitch.isChecked)

        oldSyncSettings = UploadFile.getAppSyncSettings()

        selectDriveViewModel.apply {
            selectedUserId.value = oldSyncSettings?.userId
            selectedDrive.value = oldSyncSettings?.run {
                DriveInfosController.getDrives(userId, driveId).firstOrNull()
            }
        }

        syncSettingsViewModel.apply {
            syncIntervalType.value = oldSyncSettings?.getIntervalType() ?: SyncSettings.IntervalType.IMMEDIATELY
            syncFolder.value = oldSyncSettings?.syncFolder
            saveOldPictures.value = SyncSettings.SavePicturesDate.SINCE_NOW
        }

        backupDateInput.dateValueLayout.setHint(R.string.syncSettingsSaveDateFromDateValue)
        backupDateInput.init(
            fragmentManager = supportFragmentManager,
            defaultDate = Date(),
            onDateSet = {
                val validUntil = Calendar.getInstance().apply {
                    val date = Date(it)
                    set(
                        date.year(),
                        date.month(),
                        date.day(),
                        date.hours(),
                        date.minutes()
                    )
                }.time
            },
        )

        AccountUtils.getAllUsers().observe(this) { users ->
            if (users.size > 1) {
                activeSelectDrive()
            } else {
                val currentUserDrives = DriveInfosController.getDrives(AccountUtils.currentUserId)
                if (currentUserDrives.size > 1) {
                    activeSelectDrive()
                } else {
                    selectDriveViewModel.selectedUserId.value = AccountUtils.currentUserId
                    selectDriveViewModel.selectedDrive.value = currentUserDrives.firstOrNull()
                }
            }
        }

        selectDriveViewModel.selectedDrive.observe(this) {
            it?.let {
                driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(it.preferences.color))
                driveName.text = it.name
                selectDivider.isVisible = true
                selectPath.isVisible = true
            } ?: run {
                driveIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.iconColor))
                driveName.setText(R.string.selectDriveTitle)
                selectDivider.isGone = true
                selectPath.isGone = true
            }
            if (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId ||
                selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId ||
                syncSettingsViewModel.syncFolder.value != oldSyncSettings?.syncFolder
            ) {
                syncSettingsViewModel.syncFolder.value = null
            }
            changeSaveButtonStatus()
        }

        selectPath.setOnClickListener {
            val intent = Intent(this, SelectFolderActivity::class.java).apply {
                putExtra(SelectFolderActivity.USER_ID_TAG, selectDriveViewModel.selectedUserId.value)
                putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, selectDriveViewModel.selectedDrive.value?.id)
                putExtra(SelectFolderActivity.DISABLE_SELECTED_FOLDER_TAG, Utils.ROOT_ID)
            }
            startActivityForResult(intent, SelectFolderActivity.SELECT_FOLDER_REQUEST)
        }

        syncSettingsViewModel.syncFolder.observe(this) { syncFolder ->
            val selectedUserId = selectDriveViewModel.selectedUserId.value
            val selectedDriveId = selectDriveViewModel.selectedDrive.value?.id
            if (syncFolder != null && selectedUserId != null && selectedDriveId != null) {
                FileController.getFileById(syncFolder, UserDrive(selectedUserId, selectedDriveId))?.let {
                    pathName.text = it.name
                    changeSaveButtonStatus()
                }
            } else {
                pathName.setText(R.string.selectFolderTitle)
            }
            mediaFoldersSettingsVisibility(syncFolder != null)
        }

        syncSettingsViewModel.saveOldPictures.observe(this) {
            changeSaveButtonStatus()
            syncDateValue.text = getString(it.shortTitle).lowercase()
            if (it == SyncSettings.SavePicturesDate.SINCE_DATE) {
                syncDateValue.visibility = GONE
                backupDateInput.visibility = VISIBLE
            } else {
                syncDateValue.visibility = VISIBLE
                backupDateInput.visibility = GONE
            }
        }

        syncSettingsViewModel.syncIntervalType.observe(this) {
            if (syncSettingsViewModel.syncIntervalType.value != oldSyncSettings?.getIntervalType()) editNumber++
            changeSaveButtonStatus()
            syncPeriodicityValue.text = getString(it.title).lowercase()
        }

        val syncVideoDefaultValue = true
        syncVideoSwitch.isChecked = oldSyncSettings?.syncVideo ?: syncVideoDefaultValue
        val syncAdvancedOptionsDefaultValue = false
        createDatedSubFoldersSwitch.isChecked = oldSyncSettings?.createDatedSubFolders ?: syncAdvancedOptionsDefaultValue
        deletePicturesAfterSyncSwitch.isChecked = oldSyncSettings?.deleteAfterSync ?: syncAdvancedOptionsDefaultValue

        activateSync.setOnClickListener { activateSyncSwitch.isChecked = !activateSyncSwitch.isChecked }
        activateSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSettingVisibility(isChecked)
            if (AccountUtils.isEnableAppSync() == isChecked) editNumber-- else editNumber++
            if (isChecked) permission.checkSyncPermissions()
            changeSaveButtonStatus()
        }

        mediaFolders.setOnClickListener {
            SelectMediaFoldersDialog().show(supportFragmentManager, "SyncSettingsSelectMediaFoldersDialog")
        }

        syncVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncSettings?.syncVideo ?: syncVideoDefaultValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        createDatedSubFoldersSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncSettings?.createDatedSubFolders ?: syncAdvancedOptionsDefaultValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        deletePicturesAfterSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncSettings?.deleteAfterSync ?: syncAdvancedOptionsDefaultValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        syncDate.setOnClickListener {
            SelectSaveDateBottomSheetDialog().show(supportFragmentManager, "SyncSettingsSaveDateBottomSheetDialog")
        }

        syncPeriodicity.setOnClickListener {
            SelectIntervalTypeBottomSheetDialog().show(supportFragmentManager, "SyncSettingsSelectIntervalTypeBottomSheetDialog")
        }

        saveButton.initProgress(this)
        saveButton.setOnClickListener {
            if (permission.checkSyncPermissions()) saveSettings()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SelectFolderActivity.SELECT_FOLDER_REQUEST && resultCode == RESULT_OK) {
            syncSettingsViewModel.syncFolder.value = data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)
        }
    }

    fun onDialogDismissed() {
        syncSettingsVisibility(MediaFolder.getAllSyncedFoldersCount() > 0)
        changeSaveButtonStatus()
    }

    private fun activeSelectDrive() {
        switchDrive.isVisible = true
        selectDrive.setOnClickListener {
            SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog")
        }
    }

    private fun saveSettingVisibility(isVisible: Boolean) {
        mediaFoldersSettingsVisibility(if (isVisible) syncSettingsViewModel.syncFolder.value != null else false)
        saveSettingsTitle.isVisible = isVisible
        saveSettingsLayout.isVisible = isVisible
    }

    private fun mediaFoldersSettingsVisibility(isVisible: Boolean) {
        syncSettingsVisibility(if (isVisible) MediaFolder.getAllSyncedFoldersCount() > 0 else false)
        mediaFoldersSettingsTitle.isVisible = isVisible
        mediaFoldersSettingsLayout.isVisible = isVisible
    }

    private fun syncSettingsVisibility(isVisible: Boolean) {
        syncSettingsTitle.isVisible = isVisible
        syncSettingsLayout.isVisible = isVisible
    }

    private fun changeSaveButtonStatus() {
        val allSyncedFoldersCount = MediaFolder.getAllSyncedFoldersCount().toInt()
        val isEdited = (editNumber > 0)
                || (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId)
                || (selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId)
                || (syncSettingsViewModel.syncFolder.value != oldSyncSettings?.syncFolder)
                || (syncSettingsViewModel.saveOldPictures.value != null)
                || allSyncedFoldersCount > 0
        saveButton.isVisible = isEdited

        mediaFoldersTitle.text = if (allSyncedFoldersCount == 0) getString(R.string.noSelectMediaFolders)
        else resources.getQuantityString(R.plurals.mediaFoldersSelected, allSyncedFoldersCount, allSyncedFoldersCount)

        saveButton.isEnabled = isEdited && (selectDriveViewModel.selectedUserId.value != null)
                && (selectDriveViewModel.selectedDrive.value != null)
                && (syncSettingsViewModel.syncFolder.value != null)
                && allSyncedFoldersCount > 0
    }

    private fun saveSettings() {
        saveButton.showProgress()
        lifecycleScope.launch(Dispatchers.IO) {
            val date = when (syncSettingsViewModel.saveOldPictures.value!!) {
                SyncSettings.SavePicturesDate.SINCE_NOW -> Date()
                SyncSettings.SavePicturesDate.SINCE_FOREVER -> Date(0)
                SyncSettings.SavePicturesDate.SINCE_DATE -> Date(1234567890)
            }

            if (activateSyncSwitch.isChecked) {
                val syncSettings = SyncSettings(
                    userId = selectDriveViewModel.selectedUserId.value!!,
                    driveId = selectDriveViewModel.selectedDrive.value!!.id,
                    lastSync = date,
                    syncFolder = syncSettingsViewModel.syncFolder.value!!,
                    syncVideo = syncVideoSwitch.isChecked,
                    createDatedSubFolders = createDatedSubFoldersSwitch.isChecked,
                    deleteAfterSync = deletePicturesAfterSyncSwitch.isChecked
                )
                syncSettings.setIntervalType(syncSettingsViewModel.syncIntervalType.value!!)
                UploadFile.setAppSyncSettings(syncSettings)
                activateAutoSync(syncSettings)
            } else {
                disableAutoSync()
            }

            withContext(Dispatchers.Main) {
                onBackPressed()
            }
        }
    }

    class SyncSettingsViewModel : ViewModel() {
        val saveOldPictures = MutableLiveData<SyncSettings.SavePicturesDate>()
        val syncIntervalType = MutableLiveData<SyncSettings.IntervalType>()
        val syncFolder = MutableLiveData<Int?>()
    }
}
