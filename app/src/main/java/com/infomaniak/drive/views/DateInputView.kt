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
package com.infomaniak.drive.views

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.drive.R
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.view_date_input.view.*
import java.util.*

class DateInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var currentCalendarDate: Date

    init {
        inflate(context, R.layout.view_date_input, this)
    }

    fun init(defaultDate: Date = Date(), onDateSet: ((timestamp: Long) -> Unit)? = null) {
        currentCalendarDate = defaultDate

        dateValueInput.apply {
            text = SpannableStringBuilder(currentCalendarDate.format())
            keyListener = null
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    showDatePicker(currentCalendarDate) { calendarResult ->
                        currentCalendarDate = Date(calendarResult)
                        dateValueInput.text = SpannableStringBuilder(currentCalendarDate.format())
                        onDateSet?.invoke(calendarResult)
                    }
                }
                performClick()
            }
        }
    }

    fun getCurrentTimestampValue(): Long? {
        return if (this::currentCalendarDate.isInitialized) currentCalendarDate.time / 1000 else null
    }

    private fun showDatePicker(defaultDate: Date, onDateSet: (timestamp: Long) -> Unit) {
        val yesterday = Calendar.getInstance().apply { this.add(Calendar.DATE, -1) }
        val calendarConstraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.from(yesterday.timeInMillis))
            .build()

        val materialDatePickerBuilder = MaterialDatePicker.Builder
            .datePicker()
            .setTheme(R.style.MaterialCalendarThemeBackground)
            .setSelection(defaultDate.time)
            .setCalendarConstraints(calendarConstraints)

        materialDatePickerBuilder.build().apply {
            addOnPositiveButtonClickListener { onDateSet(it) }
        }.show((context as AppCompatActivity).supportFragmentManager, materialDatePickerBuilder.toString())
    }
}