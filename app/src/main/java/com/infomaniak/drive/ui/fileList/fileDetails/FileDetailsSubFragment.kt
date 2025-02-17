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
package com.infomaniak.drive.ui.fileList.fileDetails

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.navGraphViewModels
import com.google.android.material.button.MaterialButton
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.MainViewModel

open class FileDetailsSubFragment : Fragment() {
    val fileDetailsViewModel: FileDetailsViewModel by navGraphViewModels(R.id.fileDetailsFragment)
    val mainViewModel: MainViewModel by activityViewModels()

    open val addCommentButton: MaterialButton get() = (requireParentFragment() as FileDetailsFragment).addCommentButton
}
