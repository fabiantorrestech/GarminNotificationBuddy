package com.fabiantorrestech.garminnotificationbuddy.ui

import android.app.TimePickerDialog
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.data.ScheduleEntity
import com.fabiantorrestech.garminnotificationbuddy.model.ScheduleScope

object ScheduleEditorDialog {
    fun show(
        fragment: Fragment,
        existing: ScheduleEntity?,
        scope: ScheduleScope,
        scopeKey: String?,
        onSave: (ScheduleEntity) -> Unit,
    ) {
        val context = fragment.requireContext()
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_edit_schedule, null)
        val nameInput = root.findViewById<EditText>(R.id.scheduleNameEditText)
        val startButton = root.findViewById<Button>(R.id.startTimeButton)
        val endButton = root.findViewById<Button>(R.id.endTimeButton)
        val dayChecks = listOf(
            root.findViewById<CheckBox>(R.id.sundayCheckBox),
            root.findViewById<CheckBox>(R.id.mondayCheckBox),
            root.findViewById<CheckBox>(R.id.tuesdayCheckBox),
            root.findViewById<CheckBox>(R.id.wednesdayCheckBox),
            root.findViewById<CheckBox>(R.id.thursdayCheckBox),
            root.findViewById<CheckBox>(R.id.fridayCheckBox),
            root.findViewById<CheckBox>(R.id.saturdayCheckBox),
        )

        var startMinute = existing?.startMinuteOfDay ?: (22 * 60)
        var endMinute = existing?.endMinuteOfDay ?: (7 * 60)
        nameInput.setText(existing?.name.orEmpty())
        updateTimeButton(startButton, startMinute)
        updateTimeButton(endButton, endMinute)
        dayChecks.forEachIndexed { index, checkBox ->
            checkBox.isChecked = existing?.let { (it.daysMask and (1 shl index)) != 0 } ?: true
        }

        startButton.setOnClickListener {
            showTimePicker(context, startMinute) { selectedMinute ->
                startMinute = selectedMinute
                updateTimeButton(startButton, selectedMinute)
            }
        }
        endButton.setOnClickListener {
            showTimePicker(context, endMinute) { selectedMinute ->
                endMinute = selectedMinute
                updateTimeButton(endButton, selectedMinute)
            }
        }

        AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.add_schedule_title else R.string.edit_schedule_title)
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_label) { _, _ ->
                val daysMask = dayChecks.mapIndexedNotNull { index, checkBox ->
                    if (checkBox.isChecked) 1 shl index else null
                }.fold(0) { acc, bit -> acc or bit }

                val schedule = ScheduleEntity(
                    id = existing?.id ?: 0L,
                    scopeType = scope.name,
                    scopeKey = scopeKey,
                    name = nameInput.text.toString().ifBlank {
                        context.getString(R.string.default_schedule_name)
                    },
                    daysMask = if (daysMask == 0) DEFAULT_DAYS_MASK else daysMask,
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    isEnabled = existing?.isEnabled ?: true,
                )
                onSave(schedule)
            }
            .show()
    }

    private fun showTimePicker(
        context: android.content.Context,
        initialMinutes: Int,
        onPicked: (Int) -> Unit,
    ) {
        val hours = initialMinutes / 60
        val minutes = initialMinutes % 60
        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
            onPicked(selectedHour * 60 + selectedMinute)
        }, hours, minutes, true).show()
    }

    private fun updateTimeButton(button: Button, totalMinutes: Int) {
        val hour = totalMinutes / 60
        val minute = totalMinutes % 60
        button.text = "%02d:%02d".format(hour, minute)
    }

    private const val DEFAULT_DAYS_MASK = 0b1111111
}
