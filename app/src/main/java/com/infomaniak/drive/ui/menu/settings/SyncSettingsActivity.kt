/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.drive.MatomoDrive.toFloat
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.SyncSettings.IntervalType
import com.infomaniak.drive.data.models.SyncSettings.SavePicturesDate
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.ActivitySyncSettingsBinding
import com.infomaniak.drive.ui.BaseActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.activateAutoSync
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.TimeZone

class SyncSettingsActivity : BaseActivity() {

    private val binding: ActivitySyncSettingsBinding by lazy { ActivitySyncSettingsBinding.inflate(layoutInflater) }

    private val syncSettingsViewModel: SyncSettingsViewModel by viewModels()
    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private var oldSyncSettings: SyncSettings? = null
    private var editNumber = 0

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                syncSettingsViewModel.syncFolder.value = SelectFolderActivityArgs.fromBundle(bundle).folderId
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)

        setOnBackPressed()

        val permission = DrivePermissions().apply {
            registerPermissions(this@SyncSettingsActivity)
        }

        activateSyncSwitch.isChecked = AccountUtils.isEnableAppSync()
        saveSettingVisibility(activateSyncSwitch.isChecked)

        oldSyncSettings = UploadFile.getAppSyncSettings()

        selectDriveViewModel.apply {
            selectedUserId.value = oldSyncSettings?.userId ?: AccountUtils.currentUserId
            selectedDrive.value = oldSyncSettings?.run { DriveInfosController.getDrive(userId, driveId) }
        }

        val oldIntervalTypeValue = oldSyncSettings?.getIntervalType() ?: IntervalType.IMMEDIATELY
        val oldSyncVideoValue = oldSyncSettings?.syncVideo ?: true
        val oldCreateDatedSubFoldersValue = oldSyncSettings?.createDatedSubFolders ?: false
        val oldDeleteAfterSyncValue = oldSyncSettings?.deleteAfterSync ?: false
        val oldSaveOldPicturesValue = SavePicturesDate.SINCE_NOW

        syncSettingsViewModel.apply {
            syncIntervalType.value = oldIntervalTypeValue
            syncFolder.value = oldSyncSettings?.syncFolder
            saveOldPictures.value = SavePicturesDate.SINCE_NOW
        }

        observeAllUsers()
        observeCustomDate()
        observeSelectedDrive()

        selectPath.setOnClickListener {
            Intent(this@SyncSettingsActivity, SelectFolderActivity::class.java).apply {
                putExtras(
                    SelectFolderActivityArgs(
                        userId = selectDriveViewModel.selectedUserId.value!!,
                        driveId = selectDriveViewModel.selectedDrive.value?.id!!,
                        folderId = syncSettingsViewModel.syncFolder.value ?: -1,
                        disabledFolderId = Utils.ROOT_ID,
                    ).toBundle()
                )
                selectFolderResultLauncher.launch(this)
            }
        }

        observeSyncFolder()
        observeSaveOldPictures(oldSaveOldPicturesValue)

        syncDatePicker.setOnClickListener { showSyncDatePicker() }

        observeSyncIntervalType(oldIntervalTypeValue)

        syncVideoSwitch.isChecked = oldSyncVideoValue
        createDatedSubFoldersSwitch.isChecked = oldCreateDatedSubFoldersValue
        deletePicturesAfterSyncSwitch.isChecked = oldDeleteAfterSyncValue

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
            if (oldSyncVideoValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        createDatedSubFoldersSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldCreateDatedSubFoldersValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        deletePicturesAfterSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldDeleteAfterSyncValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        syncDate.setOnClickListener {
            SelectSaveDateBottomSheetDialog().show(supportFragmentManager, "SyncSettingsSelectSaveDateBottomSheetDialog")
        }

        syncPeriodicity.setOnClickListener {
            SelectIntervalTypeBottomSheetDialog().show(supportFragmentManager, "SyncSettingsSelectIntervalTypeBottomSheetDialog")
        }

        saveButton.initProgress(this@SyncSettingsActivity)
        saveButton.setOnClickListener {
            if (permission.checkSyncPermissions()) saveSettings()
        }
    }

    private fun observeAllUsers() {
        AccountUtils.getAllUsers().observe(this@SyncSettingsActivity) { users ->
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
    }

    private fun observeCustomDate() = with(binding) {
        syncSettingsViewModel.customDate.observe(this@SyncSettingsActivity) { date ->
            syncDatePicker.apply {
                isGone = date == null
                text = date?.format(FORMAT_DATE_CLEAR_MONTH) ?: ""
            }
        }
    }

    private fun observeSelectedDrive() = with(binding) {
        selectDriveViewModel.selectedDrive.observe(this@SyncSettingsActivity) {
            it?.let {
                driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(it.preferences.color))
                driveName.text = it.name
                selectDivider.isVisible = true
                selectPath.isVisible = true
            } ?: run {
                driveIcon.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this@SyncSettingsActivity, R.color.iconColor))
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
    }

    private fun observeSyncFolder() = with(binding) {
        syncSettingsViewModel.syncFolder.observe(this@SyncSettingsActivity) { syncFolderId ->

            val selectedUserId = selectDriveViewModel.selectedUserId.value
            val selectedDriveId = selectDriveViewModel.selectedDrive.value?.id
            if (syncFolderId != null && selectedUserId != null && selectedDriveId != null) {
                FileController.getFileById(syncFolderId, UserDrive(selectedUserId, selectedDriveId))?.let {
                    pathName.text = it.name
                    changeSaveButtonStatus()
                }
            } else {
                pathName.setText(R.string.selectFolderTitle)
            }
            mediaFoldersSettingsVisibility(syncFolderId != null)
        }
    }

    private fun observeSaveOldPictures(oldSaveOldPicturesValue: SavePicturesDate) = with(binding) {
        syncSettingsViewModel.saveOldPictures.observe(this@SyncSettingsActivity) {
            if (it != oldSaveOldPicturesValue) editNumber++
            changeSaveButtonStatus()
            syncDateValue.text = getString(it.shortTitle).lowercase()
            if (it == SavePicturesDate.SINCE_DATE) {
                syncSettingsViewModel.customDate.value = Date().startOfTheDay()
                syncDatePicker.text = syncSettingsViewModel.customDate.value?.format(FORMAT_DATE_CLEAR_MONTH)
            } else {
                syncSettingsViewModel.customDate.value = null
            }
        }
    }

    private fun observeSyncIntervalType(oldIntervalTypeValue: IntervalType) = with(binding) {
        syncSettingsViewModel.syncIntervalType.observe(this@SyncSettingsActivity) {
            if (syncSettingsViewModel.syncIntervalType.value != oldIntervalTypeValue) editNumber++
            changeSaveButtonStatus()
            syncPeriodicityValue.text = getString(it.title).lowercase()
        }
    }

    private fun setOnBackPressed() {

        fun finishIfPossible() {
            if (editNumber > 0) {
                Utils.createConfirmation(
                    context = this,
                    title = getString(R.string.syncSettingsNotSavedTitle),
                    message = getString(R.string.syncSettingsNotSavedDescription),
                ) { finish() }
            } else {
                finish()
            }
        }

        binding.toolbar.setNavigationOnClickListener { finishIfPossible() }
        onBackPressedDispatcher.addCallback(this) { finishIfPossible() }
    }

    fun onDialogDismissed() {
        syncSettingsVisibility(MediaFolder.getAllSyncedFoldersCount() > 0)
        changeSaveButtonStatus()
    }

    private fun activeSelectDrive() = with(binding) {
        switchDrive.isVisible = true
        selectDrive.setOnClickListener { SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog") }
    }

    private fun saveSettingVisibility(isVisible: Boolean) = with(binding) {
        mediaFoldersSettingsVisibility(isVisible && syncSettingsViewModel.syncFolder.value != null)
        saveSettingsTitle.isVisible = isVisible
        saveSettingsLayout.isVisible = isVisible
    }

    private fun mediaFoldersSettingsVisibility(isVisible: Boolean) = with(binding) {
        syncSettingsVisibility(isVisible && MediaFolder.getAllSyncedFoldersCount() > 0)
        mediaFoldersSettingsTitle.isVisible = isVisible
        mediaFoldersSettingsLayout.isVisible = isVisible
    }

    private fun syncSettingsVisibility(isVisible: Boolean) = with(binding) {
        syncSettingsTitle.isVisible = isVisible
        syncSettingsLayout.isVisible = isVisible
    }

    private fun changeSaveButtonStatus() = with(binding) {
        val allSyncedFoldersCount = MediaFolder.getAllSyncedFoldersCount().toInt()
        val isEdited = (editNumber > 0)
                || (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId)
                || (selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId)
                || (syncSettingsViewModel.syncFolder.value != oldSyncSettings?.syncFolder)
                || (syncSettingsViewModel.saveOldPictures.value != SavePicturesDate.SINCE_NOW)
                || allSyncedFoldersCount > 0
        saveButton.isVisible = isEdited

        mediaFoldersTitle.text = if (allSyncedFoldersCount == 0) getString(R.string.noSelectMediaFolders)
        else resources.getQuantityString(R.plurals.mediaFoldersSelected, allSyncedFoldersCount, allSyncedFoldersCount)

        saveButton.isEnabled = isEdited && (selectDriveViewModel.selectedUserId.value != null)
                && (selectDriveViewModel.selectedDrive.value != null)
                && (syncSettingsViewModel.syncFolder.value != null)
                && allSyncedFoldersCount > 0
    }

    private fun trackPhotoSyncEvents(syncSettings: SyncSettings) {

        val dateName = when (syncSettingsViewModel.saveOldPictures.value!!) {
            SavePicturesDate.SINCE_NOW -> "syncNew"
            SavePicturesDate.SINCE_FOREVER -> "syncAll"
            SavePicturesDate.SINCE_DATE -> "syncFromDate"
        }

        trackPhotoSyncEvent("deleteAfterImport", syncSettings.deleteAfterSync)
        trackPhotoSyncEvent("createDatedFolders", syncSettings.createDatedSubFolders)
        trackPhotoSyncEvent("importVideo", syncSettings.syncVideo)
        trackPhotoSyncEvent(dateName)
    }

    private fun saveSettings() = with(binding) {
        saveButton.showProgress()
        lifecycleScope.launch(Dispatchers.IO) {
            val date = when (syncSettingsViewModel.saveOldPictures.value!!) {
                SavePicturesDate.SINCE_NOW -> Date()
                SavePicturesDate.SINCE_FOREVER -> Date(0)
                SavePicturesDate.SINCE_DATE -> syncSettingsViewModel.customDate.value ?: Date()
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
                trackPhotoSyncEvents(syncSettings)
                syncSettings.setIntervalType(syncSettingsViewModel.syncIntervalType.value!!)
                UploadFile.setAppSyncSettings(syncSettings)
                activateAutoSync(syncSettings)
            } else {
                disableAutoSync()
            }

            trackPhotoSyncEvent(if (activateSyncSwitch.isChecked) "enabled" else "disabled")

            finish()
        }
    }

    private fun showSyncDatePicker() {
        val calendarConstraints = CalendarConstraints.Builder()
            .setEnd(Date().time)
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val dateToSet = syncSettingsViewModel.customDate.value ?: Date()

        MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.MaterialCalendarThemeBackground)
            .setSelection(
                // Need to account for time zones as MaterialDatePicker expects GMT+0. Else setting the picker to anything between
                // 02.01.22 00:00:00 and 02.01.22 00:59:59 results in the date picker being set to 01.01.22 instead when the device uses GMT+1
                dateToSet.time + TimeZone.getDefault().getOffset(dateToSet.time)
            )
            .setCalendarConstraints(calendarConstraints)
            .build()
            .apply {
                addOnPositiveButtonClickListener { syncSettingsViewModel.customDate.value = Date(it).startOfTheDay() }
                show(supportFragmentManager, "syncDatePicker")
            }
    }

    class SyncSettingsViewModel : ViewModel() {
        val customDate = MutableLiveData<Date>()
        val saveOldPictures = MutableLiveData<SavePicturesDate>()
        val syncIntervalType = MutableLiveData<IntervalType>()
        val syncFolder = MutableLiveData<Int?>()
    }

    private fun trackPhotoSyncEvent(name: String, value: Boolean? = null) {
        trackEvent("photoSync", name, value = value?.toFloat())
    }
}
