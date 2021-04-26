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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import com.infomaniak.drive.R
import kotlinx.android.synthetic.main.fragment_file_details.*
import kotlinx.android.synthetic.main.fragment_file_details_activities.*

class FileDetailsActivitiesFragment : FileDetailsSubFragment() {

    private lateinit var activitiesAdapter: FileActivitiesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_details_activities, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        fileDetailsViewModel.currentFile.observe(viewLifecycleOwner) { currentFile ->
            activitiesAdapter = FileActivitiesAdapter(currentFile.isFolder()).apply {
                isComplete = false
                fileDetailsViewModel.getFileActivities(currentFile).observe(viewLifecycleOwner) { apiResponse ->
                    apiResponse?.data?.let { activities ->
                        addAll(activities)
                        isComplete = apiResponse.page == apiResponse.pages
                    } ?: also {
                        isComplete = true
                    }
                }
            }
            fileActivitiesRecyclerView.adapter = activitiesAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        requireParentFragment().addCommentButton.visibility = GONE
    }
}