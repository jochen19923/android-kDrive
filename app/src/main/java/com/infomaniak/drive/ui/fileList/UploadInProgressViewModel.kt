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
package com.infomaniak.drive.ui.fileList

import android.app.Application
import android.content.Context
import android.util.ArrayMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers

class UploadInProgressViewModel(application: Application) : AndroidViewModel(application) {

    private inline val context: Context get() = getApplication<Application>().applicationContext

    fun getPendingFolders() = liveData<ArrayList<File>>(Dispatchers.IO) {
        UploadFile.getRealmInstance().use { realm ->
            UploadFile.getAllPendingFolders(realm)?.let { pendingFolders ->
                emit(generateFolderFiles(pendingFolders))
            } ?: emit(arrayListOf())
        }
    }

    private fun generateFolderFiles(pendingFolders: RealmResults<UploadFile>): ArrayList<File> {
        val files = arrayListOf<File>()
        val drivesNames = ArrayMap<Int, String>()

        pendingFolders.forEach { uploadFile ->
            val driveId = uploadFile.driveId
            val isSharedWithMe = driveId != AccountUtils.currentDriveId

            val driveName = if (isSharedWithMe && drivesNames[driveId] == null) {
                val drive = DriveInfosController.getDrives(AccountUtils.currentUserId, driveId, null).first()
                drivesNames[driveId] = drive.name
                drive.name

            } else {
                drivesNames[driveId]
            }

            val userDrive = UserDrive(driveId = driveId, sharedWithMe = isSharedWithMe, driveName = driveName)
            files.add(createFolderFile(uploadFile.remoteFolder, userDrive))
        }

        return files
    }

    private fun createFolderFile(fileId: Int, userDrive: UserDrive): File {
        val folder = FileController.getFileById(fileId, userDrive)!!
        val name: String
        val type: String

        if (fileId == Utils.ROOT_ID) {
            name = Utils.getRootName(context)
            type = File.Type.DRIVE.value
        } else {
            name = folder.name
            type = File.Type.FOLDER.value
        }

        return File(
            id = fileId,
            isFromUploads = true,
            name = name,
            path = folder.getRemotePath(userDrive),
            type = type
        )
    }
}