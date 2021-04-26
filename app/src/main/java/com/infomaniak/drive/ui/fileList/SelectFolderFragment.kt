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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.SaveExternalViewModel
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class SelectFolderFragment : FileListFragment() {

    private lateinit var saveExternalViewModel: SaveExternalViewModel
    private val navigationArgs: SelectFolderFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles: Boolean = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        saveExternalViewModel = ViewModelProvider(requireActivity())[SaveExternalViewModel::class.java]
        userDrive = saveExternalViewModel.userDrive
        super.onActivityCreated(savedInstanceState)

        folderName = if (folderID == ROOT_ID) saveExternalViewModel.currentDrive?.name ?: "/" else navigationArgs.folderName

        collapsingToolbarLayout.title = getString(R.string.selectFolderTitle)

        val currentFolder = FileController.getFileById(folderID, userDrive)

        toolbar.menu.apply {
            findItem(R.id.addFolderItem).apply {
                setOnMenuItemClickListener {
                    (requireActivity() as? SelectFolderActivity)?.hideSaveButton()
                    safeNavigate(
                        SelectFolderFragmentDirections.actionSelectFolderFragmentToNewFolderFragment(
                            parentFolderId = folderID,
                            userDrive = userDrive
                        )
                    )
                    true
                }
                isVisible = currentFolder?.rights?.newFolder == true
            }
        }

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            onBackPressed()
        }

        fileRecyclerView.isItemDraggable = false
        fileAdapter.selectFolder = true
        fileAdapter.onFileClicked = { file ->
            when {
                file.isFolder() -> {
                    if (!file.isDisabled()) {
                        fileListViewModel.cancelDownloadFiles()
                        safeNavigate(
                            SelectFolderFragmentDirections.fileListFragmentToFileListFragment(
                                folderID = file.id,
                                folderName = file.name,
                                ignoreCreateFolderStack = false
                            )
                        )
                    }
                }
            }
        }
        val selectFolderActivity = requireActivity() as SelectFolderActivity
        selectFolderActivity.showSaveButton()
        val enable = folderID != saveExternalViewModel.disableSelectedFolder &&
                (currentFolder?.rights?.moveInto == true || currentFolder?.rights?.newFile == true)
        selectFolderActivity.enableSaveButton(enable)
    }

    private fun onBackPressed() {
        if (folderID == ROOT_ID) {
            requireActivity().finish()
        } else Utils.ignoreCreateFolderBackStack(findNavController(), true)

    }
}