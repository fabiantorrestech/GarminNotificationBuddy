package com.fabiantorrestech.garminnotificationbuddy.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.MainActivity
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.data.AppListItem
import com.fabiantorrestech.garminnotificationbuddy.data.ScheduleEntity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ScheduleDetailFragment : Fragment(R.layout.fragment_schedule_detail) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }
    private val scheduleId: Long? by lazy {
        arguments?.getLong(ARG_SCHEDULE_ID)?.takeIf { it > 0L }
    }
    private val preselectedPackageName: String? by lazy {
        arguments?.getString(ARG_PRESELECTED_PACKAGE_NAME)?.takeIf { it.isNotBlank() }
    }
    private val filterMode = MutableStateFlow(AppFilterMode.USER_APPS)
    private val selectedPackages = linkedSetOf<String>()

    private var startMinute = 22 * 60
    private var endMinute = 7 * 60
    private var scheduleName = ""
    private var hasBoundSchedule = false
    private var hasBoundAssignments = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scheduleNameView = view.findViewById<TextView>(R.id.scheduleNameTextView)
        val editNameButton = view.findViewById<MaterialButton>(R.id.editScheduleNameButton)
        val scheduleDetailSubtitle = view.findViewById<TextView>(R.id.scheduleDetailSubtitleTextView)
        val enabledSwitch = view.findViewById<SwitchMaterial>(R.id.scheduleEnabledSwitch)
        val startButton = view.findViewById<Button>(R.id.startTimeButton)
        val endButton = view.findViewById<Button>(R.id.endTimeButton)
        val assignedCountView = view.findViewById<TextView>(R.id.assignedAppsCountTextView)
        val userAppsButton = view.findViewById<MaterialButton>(R.id.userAppsFilterButton)
        val allInstalledButton = view.findViewById<MaterialButton>(R.id.allInstalledFilterButton)
        val emptyAppsView = view.findViewById<TextView>(R.id.emptyScheduleAppsTextView)
        val saveButton = view.findViewById<Button>(R.id.saveScheduleButton)
        val deleteButton = view.findViewById<Button>(R.id.deleteScheduleButton)
        val dayChecks = listOf(
            view.findViewById<CheckBox>(R.id.sundayCheckBox),
            view.findViewById<CheckBox>(R.id.mondayCheckBox),
            view.findViewById<CheckBox>(R.id.tuesdayCheckBox),
            view.findViewById<CheckBox>(R.id.wednesdayCheckBox),
            view.findViewById<CheckBox>(R.id.thursdayCheckBox),
            view.findViewById<CheckBox>(R.id.fridayCheckBox),
            view.findViewById<CheckBox>(R.id.saturdayCheckBox),
        )

        scheduleDetailSubtitle.text = getString(
            if (scheduleId == null) R.string.add_schedule_title else R.string.schedule_detail_title,
        )
        if (scheduleId == null) {
            preselectedPackageName?.let(selectedPackages::add)
            hasBoundAssignments = true
            scheduleName = getString(R.string.default_schedule_name)
        }
        scheduleNameView.text = scheduleName.ifBlank { getString(R.string.default_schedule_name) }
        dayChecks.forEach { it.isChecked = true }
        updateTimeButton(startButton, startMinute)
        updateTimeButton(endButton, endMinute)
        updateAssignedCount(assignedCountView)
        bindFilterButtons(userAppsButton, allInstalledButton)

        startButton.setOnClickListener {
            showTimePicker(startMinute) { selectedMinute ->
                startMinute = selectedMinute
                updateTimeButton(startButton, selectedMinute)
            }
        }
        endButton.setOnClickListener {
            showTimePicker(endMinute) { selectedMinute ->
                endMinute = selectedMinute
                updateTimeButton(endButton, selectedMinute)
            }
        }
        userAppsButton.setOnClickListener {
            filterMode.value = AppFilterMode.USER_APPS
            bindFilterButtons(userAppsButton, allInstalledButton)
        }
        allInstalledButton.setOnClickListener {
            filterMode.value = AppFilterMode.ALL_INSTALLED
            bindFilterButtons(userAppsButton, allInstalledButton)
        }
        editNameButton.setOnClickListener {
            showEditNameDialog(
                currentName = scheduleName.ifBlank { getString(R.string.default_schedule_name) },
            ) { updatedName ->
                scheduleName = updatedName
                scheduleNameView.text = updatedName
            }
        }
        deleteButton.visibility = if (scheduleId == null) View.GONE else View.VISIBLE
        deleteButton.setOnClickListener {
            val existingScheduleId = scheduleId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.deleteSchedule(existingScheduleId)
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        saveButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val schedule = ScheduleEntity(
                    id = scheduleId ?: 0L,
                    name = scheduleName.ifBlank {
                        getString(R.string.default_schedule_name)
                    },
                    daysMask = selectedDaysMask(dayChecks),
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    isEnabled = enabledSwitch.isChecked,
                )
                container.ruleRepository.saveSchedule(schedule, selectedPackages.toSet())
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        var visibleApps = emptyList<AppListItem>()
        lateinit var adapter: AppRuleAdapter
        adapter = AppRuleAdapter(
            bindPrimaryButton = { button, item ->
                bindAssignmentStateButton(button, selectedPackages.contains(item.packageName))
            },
            onPrimaryClicked = { item ->
                val wasAdded = selectedPackages.add(item.packageName)
                if (!wasAdded) {
                    selectedPackages.remove(item.packageName)
                }
                adapter.submitList(visibleApps)
                updateAssignedCount(assignedCountView)
            },
            secondaryButtonText = getString(R.string.details_label),
            onSecondaryClicked = { item ->
                (requireActivity() as MainActivity).openAppDetail(item.packageName)
            },
        )
        view.findViewById<RecyclerView>(R.id.scheduleAppsRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            container.ruleRepository.refreshAppList()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    val existingScheduleId = scheduleId
                    if (existingScheduleId == null) {
                        return@launch
                    }
                    container.ruleRepository.observeSchedule(existingScheduleId).collect { schedule ->
                        if (schedule != null && !hasBoundSchedule) {
                            bindSchedule(
                                schedule = schedule,
                                scheduleNameView = scheduleNameView,
                                enabledSwitch = enabledSwitch,
                                dayChecks = dayChecks,
                                startButton = startButton,
                                endButton = endButton,
                            )
                            hasBoundSchedule = true
                        }
                    }
                }
                launch {
                    val existingScheduleId = scheduleId
                    if (existingScheduleId == null) {
                        return@launch
                    }
                    container.ruleRepository.observeScheduleAssignments(existingScheduleId).collect { assignments ->
                        if (!hasBoundAssignments) {
                            selectedPackages.clear()
                            selectedPackages.addAll(assignments)
                            hasBoundAssignments = true
                            updateAssignedCount(assignedCountView)
                        }
                    }
                }
                launch {
                    combine(
                        container.ruleRepository.observeAppListItems(),
                        filterMode,
                    ) { items, selectedFilter ->
                        items.filter { item ->
                            matchesFilterMode(item, selectedFilter) || selectedPackages.contains(item.packageName)
                        }
                    }.collect { items ->
                        visibleApps = items
                        adapter.submitList(items)
                        emptyAppsView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        updateAssignedCount(assignedCountView)
                    }
                }
            }
        }
    }

    private fun bindSchedule(
        schedule: ScheduleEntity,
        scheduleNameView: TextView,
        enabledSwitch: SwitchMaterial,
        dayChecks: List<CheckBox>,
        startButton: Button,
        endButton: Button,
    ) {
        scheduleName = schedule.name
        scheduleNameView.text = scheduleName
        enabledSwitch.isChecked = schedule.isEnabled
        startMinute = schedule.startMinuteOfDay
        endMinute = schedule.endMinuteOfDay
        updateTimeButton(startButton, startMinute)
        updateTimeButton(endButton, endMinute)
        dayChecks.forEachIndexed { index, checkBox ->
            checkBox.isChecked = (schedule.daysMask and (1 shl index)) != 0
        }
    }

    private fun selectedDaysMask(dayChecks: List<CheckBox>): Int {
        val daysMask = dayChecks.mapIndexedNotNull { index, checkBox ->
            if (checkBox.isChecked) 1 shl index else null
        }.fold(0) { acc, bit -> acc or bit }
        return if (daysMask == 0) DEFAULT_DAYS_MASK else daysMask
    }

    private fun bindFilterButtons(
        userAppsButton: MaterialButton,
        allInstalledButton: MaterialButton,
    ) {
        userAppsButton.isChecked = filterMode.value == AppFilterMode.USER_APPS
        allInstalledButton.isChecked = filterMode.value == AppFilterMode.ALL_INSTALLED
    }

    private fun matchesFilterMode(item: AppListItem, selectedFilter: AppFilterMode): Boolean {
        return when (selectedFilter) {
            AppFilterMode.USER_APPS -> !item.isSystemApp || item.keepVisibleInUserApps
            AppFilterMode.ALL_INSTALLED -> item.isInstalled || item.keepVisibleInUserApps
        }
    }

    private fun updateAssignedCount(view: TextView) {
        view.text = getString(R.string.schedule_assigned_count_template, selectedPackages.size)
    }

    private fun showEditNameDialog(
        currentName: String,
        onNameUpdated: (String) -> Unit,
    ) {
        val input = EditText(requireContext()).apply {
            setText(currentName)
            setSelection(text.length)
            hint = getString(R.string.schedule_name_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_schedule_name_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_label) { _, _ ->
                onNameUpdated(
                    input.text.toString().trim().ifBlank {
                        getString(R.string.default_schedule_name)
                    },
                )
            }
            .show()
    }

    private fun showTimePicker(
        initialMinutes: Int,
        onPicked: (Int) -> Unit,
    ) {
        val hours = initialMinutes / 60
        val minutes = initialMinutes % 60
        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            onPicked(selectedHour * 60 + selectedMinute)
        }, hours, minutes, true).show()
    }

    private fun updateTimeButton(button: Button, totalMinutes: Int) {
        val hour = totalMinutes / 60
        val minute = totalMinutes % 60
        button.text = "%02d:%02d".format(hour, minute)
    }

    companion object {
        private const val ARG_SCHEDULE_ID = "schedule_id"
        private const val ARG_PRESELECTED_PACKAGE_NAME = "preselected_package_name"
        private const val DEFAULT_DAYS_MASK = 0b1111111

        fun newInstance(
            scheduleId: Long? = null,
            preselectedPackageName: String? = null,
        ): ScheduleDetailFragment {
            return ScheduleDetailFragment().apply {
                arguments = Bundle().apply {
                    scheduleId?.let { putLong(ARG_SCHEDULE_ID, it) }
                    putString(ARG_PRESELECTED_PACKAGE_NAME, preselectedPackageName)
                }
            }
        }
    }

    private enum class AppFilterMode {
        USER_APPS,
        ALL_INSTALLED,
    }
}
