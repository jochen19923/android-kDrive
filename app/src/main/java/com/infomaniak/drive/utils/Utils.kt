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
package com.infomaniak.drive.utils

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.view.View
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.work.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.getCloudAndFileUris
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragmentArgs
import com.infomaniak.lib.core.utils.showToast
import kotlinx.android.synthetic.main.dialog_download_progress.view.*
import kotlinx.android.synthetic.main.dialog_name_prompt.view.*
import kotlinx.android.synthetic.main.dialog_name_prompt.view.icon
import java.util.*
import kotlin.math.min
import kotlin.math.pow

object Utils {

    const val ROOT_ID = 1
    const val OTHER_ROOT_ID = -1

    const val INDETERMINATE_PROGRESS = -1

    fun createConfirmation(
        context: Context,
        title: String,
        message: String? = null,
        buttonText: String? = null,
        autoDismiss: Boolean = true,
        isDeletion: Boolean = false,
        onConfirmation: (dialog: Dialog) -> Unit
    ) {
        val style = if (isDeletion) R.style.DeleteDialogStyle else R.style.DialogStyle
        val dialog = MaterialAlertDialogBuilder(context, style)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText ?: context.getString(R.string.buttonConfirm)) { _, _ -> }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            onConfirmation(dialog)
            if (autoDismiss) {
                dialog.dismiss()
            } else {
                it.isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            }
        }
    }

    fun confirmFileDeletion(
        context: Context,
        fileName: String? = null,
        fileCount: Int = 1,
        fromTrash: Boolean = false,
        onConfirmation: (dialog: Dialog) -> Unit
    ) {
        val title: Int = if (fromTrash) R.string.modalDeleteTitle else R.string.modalMoveTrashTitle
        val message: String = context.resources.getQuantityString(
            if (fromTrash) R.plurals.modalDeleteDescription
            else R.plurals.modalMoveTrashDescription,
            if (fileName == null) fileCount else 1,
            fileName ?: fileCount
        )
        val button: Int = if (fromTrash) R.string.buttonDelete else R.string.buttonMove

        createConfirmation(
            context = context,
            title = context.getString(title),
            message = message,
            autoDismiss = false,
            isDeletion = true,
            buttonText = context.getString(button),
            onConfirmation = onConfirmation
        )
    }

    fun getRootName(context: Context): String {
        return context.getString(R.string.allRootName, AccountUtils.getCurrentDrive()?.name)
    }

    fun createPromptNameDialog(
        context: Context,
        @StringRes title: Int,
        @StringRes fieldName: Int,
        @StringRes positiveButton: Int,
        @DrawableRes iconRes: Int? = null,
        fieldValue: String? = null,
        selectedRange: Int? = null,
        onPositiveButtonClicked: (dialog: Dialog, body: String) -> Unit
    ) {
        val promptLayoutView = View.inflate(context, R.layout.dialog_name_prompt, null)
        val nameEditText = promptLayoutView.nameEditText
        promptLayoutView.nameLayout.setHint(fieldName)

        iconRes?.let {
            promptLayoutView.icon.apply {
                setImageDrawable(ContextCompat.getDrawable(context, it))
                isVisible = true
            }
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.DialogStyle)
            .setTitle(title)
            .setView(promptLayoutView)
            .setPositiveButton(positiveButton) { _, _ -> }
            .setNegativeButton(R.string.buttonCancel) { _, _ -> }
            .setCancelable(false)
            .create()
            .apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                show()
            }

        val buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        nameEditText.apply {
            doOnTextChanged { text, _, _, _ -> buttonPositive.isEnabled = text?.length != 0 }
            fieldValue?.let {
                setText(it)
                selectedRange?.let { range -> setSelection(0, range) }
                requestFocus()
            }
        }

        buttonPositive.setOnClickListener {
            onPositiveButtonClicked(dialog, nameEditText.text.toString().trim())
            it.isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
    }

    /**
     * Specific method which allows to popBackstack by ignoring create folder "stack" (in case of folder creation)
     */
    fun ignoreCreateFolderBackStack(navController: NavController, mustIgnore: Boolean) {
        when {
            mustIgnore -> if (!navController.popBackStack(R.id.newFolderFragment, true)) navController.popBackStack()
            else -> navController.popBackStack()
        }
    }

    fun displayFile(
        mainViewModel: MainViewModel,
        navController: NavController,
        selectedFile: File,
        fileList: ArrayList<File>,
        isSharedWithMe: Boolean = false,
    ) {
        mainViewModel.currentPreviewFileList = fileList.associateBy { it.id } as LinkedHashMap<Int, File>
        val bundle = PreviewSliderFragmentArgs(
            fileId = selectedFile.id,
            driveId = selectedFile.driveId,
            isSharedWithMe = isSharedWithMe,
            hideActions = selectedFile.isFromActivities
        ).toBundle()
        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.fragment_open_enter)
            .setExitAnim(R.anim.fragment_open_exit)
            .build()
        navController.navigate(R.id.previewSliderFragment, bundle, navOptions)
    }

    fun convertBytesToGigaBytes(bytes: Long) = (bytes / 1024.0.pow(3))
    fun convertGigaByteToBytes(gigaBytes: Double) = (gigaBytes * 1024.0.pow(3)).toLong()

    fun Context.moveFileClicked(
        disabledFolderId: Int?,
        selectFolderResultLauncher: ActivityResultLauncher<Intent>,
        mainViewModel: MainViewModel
    ) {
        mainViewModel.ignoreSyncOffline = true
        Intent(this, SelectFolderActivity::class.java).apply {
            putExtras(
                SelectFolderActivityArgs(
                    userId = AccountUtils.currentUserId,
                    userDriveId = AccountUtils.currentDriveId,
                    disabledFolderId = disabledFolderId ?: -1,
                    customArgs = bundleOf(SelectFolderActivity.BULK_OPERATION_CUSTOM_TAG to BulkOperationType.MOVE)
                ).toBundle()
            )
            selectFolderResultLauncher.launch(this)
        }
    }

    fun Context.openWith(file: File, userDrive: UserDrive = UserDrive()) {
        try {
            startActivity(openWithIntent(file, userDrive))
        } catch (e: ActivityNotFoundException) {
            showToast(R.string.allActivityNotFoundError)
        }
    }

    fun Context.openWithIntent(file: File, userDrive: UserDrive = UserDrive()): Intent {
        val (cloudUri, uri) = file.getCloudAndFileUris(this, userDrive)
        return Intent().apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            setDataAndType(uri, contentResolver.getType(cloudUri))
        }
    }

    fun moveCacheFileToOffline(file: File, cacheFile: java.io.File, offlineFile: java.io.File) {
        if (offlineFile.exists()) offlineFile.delete()
        cacheFile.copyTo(offlineFile)
        cacheFile.delete()
        offlineFile.setLastModified(file.getLastModifiedInMilliSecond())
    }

    fun downloadAsOfflineFile(context: Context, file: File, userDrive: UserDrive = UserDrive()) {
        val workManager = WorkManager.getInstance(context)

        if (file.isPendingOffline(context)) workManager.cancelAllWorkByTag(file.getWorkerTag())
        val inputData = workDataOf(
            DownloadWorker.FILE_ID to file.id,
            DownloadWorker.FILE_NAME to file.name,
            DownloadWorker.USER_ID to userDrive.userId,
            DownloadWorker.DRIVE_ID to userDrive.driveId,
        )
        val networkType = if (AppSettings.onlyWifiSync) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(file.getWorkerTag())
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(DownloadWorker.TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, downloadRequest)
    }

    fun getInvalidFileNameCharacter(fileName: String): String? = Regex("[\\\\/:*?\"<>|]").find(fileName)?.value

    fun showSnackbar(
        view: View,
        title: String,
        anchorView: View? = null,
        actionButtonTitle: Int = R.string.buttonCancel,
        onActionClicked: (() -> Unit)? = null
    ) {
        Snackbar.make(view, title, Snackbar.LENGTH_LONG).apply {
            anchorView?.let { this.anchorView = it }
            onActionClicked?.let { action ->
                setAction(actionButtonTitle) {
                    action()
                }
            }
            show()
        }
    }

    fun showSnackbar(
        view: View,
        title: Int,
        anchorView: View? = null,
        actionButtonTitle: Int = R.string.buttonCancel,
        onActionClicked: (() -> Unit)? = null
    ) {
        showSnackbar(view, view.context.getString(title), anchorView, actionButtonTitle, onActionClicked)
    }

    fun copyDataToUploadCache(context: Context, file: java.io.File, fileModifiedAt: Date): Uri {
        val folder = java.io.File(context.cacheDir, UploadWorker.UPLOAD_FOLDER).apply { if (!exists()) mkdirs() }
        val outputFile = java.io.File(folder, file.toUri().hashCode().toString()).apply {
            if (exists()) delete()
            setLastModified(fileModifiedAt.time)
        }
        file.copyTo(outputFile, true)
        return outputFile.toUri()
    }

    /**
     * From file path
     */
    @Deprecated(message = "Only for API 28 and below, otherwise use ThumbnailUtils.createImageThumbnail()")
    fun extractThumbnail(filePath: String, width: Int, height: Int): Bitmap? {
        val bitmapOptions = BitmapFactory.Options()
        bitmapOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(filePath, bitmapOptions)

        val widthScale = bitmapOptions.outWidth.toFloat() / width
        val heightScale = bitmapOptions.outHeight.toFloat() / height
        val scale = min(widthScale, heightScale)
        var sampleSize = 1
        while (sampleSize < scale) {
            sampleSize *= 2
        }
        bitmapOptions.inSampleSize = sampleSize
        bitmapOptions.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(filePath, bitmapOptions)
    }

    fun getRealPathFromExternalStorage(context: Context, uri: Uri): String {
        // ExternalStorageProvider
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":").dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = split.first()
        val relativePath = split.getOrNull(1) ?: return ""
        val external = context.externalMediaDirs
        return when {
            "primary".equals(type, true) -> Environment.getExternalStorageDirectory().toString() + "/" + relativePath
            external.size > 1 -> {
                val filePath = external[1].absolutePath
                filePath.substring(0, filePath.indexOf("Android")) + relativePath
            }
            else -> ""
        }
    }

    fun createProgressDialog(context: Context, title: Int): AlertDialog {
        return MaterialAlertDialogBuilder(context, R.style.DialogStyle).apply {
            setTitle(title)
            setCancelable(false)
            View.inflate(context, R.layout.dialog_download_progress, null).apply {
                icon.isGone = true
                downloadProgress.isIndeterminate = true
                setView(this)
            }
        }.show()
    }
}
