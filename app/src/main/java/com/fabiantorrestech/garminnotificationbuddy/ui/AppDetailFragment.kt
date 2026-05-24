package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.data.AppRuleEntity
import com.fabiantorrestech.garminnotificationbuddy.model.BurstStrategy
import com.fabiantorrestech.garminnotificationbuddy.model.ScheduleScope
import com.fabiantorrestech.garminnotificationbuddy.model.toBurstStrategyOrDefault
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class AppDetailFragment : Fragment(R.layout.fragment_app_detail) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }
    private val packageName: String by lazy {
        requireArguments().getString(ARG_PACKAGE_NAME).orEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appNameView = view.findViewById<TextView>(R.id.detailAppNameTextView)
        val packageView = view.findViewById<TextView>(R.id.detailPackageNameTextView)
        val appEnabledSwitch = view.findViewById<SwitchMaterial>(R.id.detailAppEnabledSwitch)
        val defaultActionButton = view.findViewById<MaterialButton>(R.id.detailDefaultActionButton)
        val mirrorOverrideSwitch =
            view.findViewById<SwitchMaterial>(R.id.detailMirrorPacingOverrideSwitch)
        val mirrorCooldownInput = view.findViewById<EditText>(R.id.detailMirrorCooldownEditText)
        val mirrorBurstStrategyButton =
            view.findViewById<MaterialButton>(R.id.detailMirrorBurstStrategyButton)
        val saveMirrorPacingButton =
            view.findViewById<Button>(R.id.saveMirrorPacingOverrideButton)
        val allowlistInput = view.findViewById<EditText>(R.id.allowlistEditText)
        val blocklistInput = view.findViewById<EditText>(R.id.blocklistEditText)
        val saveKeywordsButton = view.findViewById<Button>(R.id.saveKeywordsButton)
        val emptyChannelsView = view.findViewById<TextView>(R.id.emptyChannelsTextView)
        val emptySchedulesView = view.findViewById<TextView>(R.id.emptyAppSchedulesTextView)

        var selectedBurstStrategy = BurstStrategy.LATEST_ONLY
        var currentAppRule: AppRuleEntity? = null
        var currentGlobalCooldown = 5
        var currentGlobalBurstStrategy = BurstStrategy.LATEST_ONLY

        val channelAdapter = ChannelRuleAdapter(
            onEnabledChanged = { item, isEnabled ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.setChannelEnabled(item.packageName, item.channelId, isEnabled)
                }
            },
            onOverrideClicked = { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.setChannelOverride(
                        item.packageName,
                        item.channelId,
                        nextChannelOverride(item.overrideAction),
                    )
                }
            },
        )
        view.findViewById<RecyclerView>(R.id.channelRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = channelAdapter
        }

        val scheduleAdapter = ScheduleAdapter(
            onEditClicked = { schedule ->
                ScheduleEditorDialog.show(
                    fragment = this,
                    existing = schedule,
                    scope = ScheduleScope.APP,
                    scopeKey = packageName,
                ) { updatedSchedule ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        container.ruleRepository.saveSchedule(updatedSchedule)
                    }
                }
            },
            onDeleteClicked = { schedule ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.deleteSchedule(schedule.id)
                }
            },
        )
        view.findViewById<RecyclerView>(R.id.appScheduleRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }

        view.findViewById<Button>(R.id.addAppScheduleButton).setOnClickListener {
            ScheduleEditorDialog.show(
                fragment = this,
                existing = null,
                scope = ScheduleScope.APP,
                scopeKey = packageName,
            ) { newSchedule ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.saveSchedule(newSchedule)
                }
            }
        }

        mirrorOverrideSwitch.setOnCheckedChangeListener { _, _ ->
            updateMirrorOverrideControls(
                mirrorOverrideSwitch = mirrorOverrideSwitch,
                mirrorCooldownInput = mirrorCooldownInput,
                mirrorBurstStrategyButton = mirrorBurstStrategyButton,
                saveMirrorPacingButton = saveMirrorPacingButton,
            )
        }
        mirrorBurstStrategyButton.setOnClickListener {
            selectedBurstStrategy = nextBurstStrategy(selectedBurstStrategy)
            bindBurstStrategyLabel(mirrorBurstStrategyButton, selectedBurstStrategy)
        }
        saveMirrorPacingButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.saveAppMirrorPacingOverride(
                    packageName = packageName,
                    isOverrideEnabled = mirrorOverrideSwitch.isChecked,
                    cooldownSeconds = mirrorCooldownInput.text.toString()
                        .toIntOrNull()
                        ?.coerceAtLeast(0)
                        ?: currentAppRule?.mirrorCooldownSecondsOverride
                        ?: currentGlobalCooldown,
                    burstStrategy = if (mirrorOverrideSwitch.isChecked) selectedBurstStrategy else null,
                )
            }
        }
        saveKeywordsButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.saveKeywordRule(
                    packageName = packageName,
                    allowlistCsv = allowlistInput.text.toString(),
                    blocklistCsv = blocklistInput.text.toString(),
                )
            }
        }

        packageView.text = packageName

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    container.ruleRepository.observeAppRule(packageName).collect { appRule ->
                        currentAppRule = appRule
                        bindAppRule(
                    appRule = appRule,
                    appNameView = appNameView,
                    appEnabledSwitch = appEnabledSwitch,
                    defaultActionButton = defaultActionButton,
                    mirrorOverrideSwitch = mirrorOverrideSwitch,
                    mirrorCooldownInput = mirrorCooldownInput,
                    mirrorBurstStrategyButton = mirrorBurstStrategyButton,
                    saveMirrorPacingButton = saveMirrorPacingButton,
                    currentGlobalCooldown = currentGlobalCooldown,
                )
                        selectedBurstStrategy = appRule?.mirrorBurstStrategyOverride
                            .toBurstStrategyOrDefault(currentGlobalBurstStrategy)
                        bindBurstStrategyLabel(mirrorBurstStrategyButton, selectedBurstStrategy)
                    }
                }
                launch {
                    container.ruleRepository.observeGlobalSettings().collect { settings ->
                        currentGlobalCooldown = settings.mirrorCooldownSeconds
                        currentGlobalBurstStrategy = settings.mirrorBurstStrategy.toBurstStrategyOrDefault()
                    }
                }
                launch {
                    container.ruleRepository.observeChannelRules(packageName).collect { channels ->
                        channelAdapter.submitList(channels)
                        emptyChannelsView.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    container.ruleRepository.observeKeywordRule(packageName).collect { keywords ->
                        if (!allowlistInput.hasFocus()) {
                            allowlistInput.setText(keywords?.allowlistCsv.orEmpty())
                        }
                        if (!blocklistInput.hasFocus()) {
                            blocklistInput.setText(keywords?.blocklistCsv.orEmpty())
                        }
                    }
                }
                launch {
                    container.ruleRepository.observeSchedules(ScheduleScope.APP, packageName).collect { schedules ->
                        scheduleAdapter.submitList(schedules)
                        emptySchedulesView.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun bindAppRule(
        appRule: AppRuleEntity?,
        appNameView: TextView,
        appEnabledSwitch: SwitchMaterial,
        defaultActionButton: MaterialButton,
        mirrorOverrideSwitch: SwitchMaterial,
        mirrorCooldownInput: EditText,
        mirrorBurstStrategyButton: MaterialButton,
        saveMirrorPacingButton: Button,
        currentGlobalCooldown: Int,
    ) {
        val name = appRule?.appName ?: packageName
        appNameView.text = name

        appEnabledSwitch.setOnCheckedChangeListener(null)
        appEnabledSwitch.isChecked = appRule?.isEnabled == true
        appEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.setAppEnabled(packageName, isChecked)
            }
        }

        val currentAction = appRule?.defaultAction ?: "BLOCK"
        defaultActionButton.text = getString(R.string.default_action_template, currentAction)
        defaultActionButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.setAppDefaultAction(packageName, nextAction(currentAction))
            }
        }

        val isOverrideEnabled = appRule?.mirrorCooldownSecondsOverride != null ||
            appRule?.mirrorBurstStrategyOverride != null
        mirrorOverrideSwitch.setOnCheckedChangeListener(null)
        mirrorOverrideSwitch.isChecked = isOverrideEnabled
        mirrorOverrideSwitch.setOnCheckedChangeListener { _, _ ->
            updateMirrorOverrideControls(
                mirrorOverrideSwitch = mirrorOverrideSwitch,
                mirrorCooldownInput = mirrorCooldownInput,
                mirrorBurstStrategyButton = mirrorBurstStrategyButton,
                saveMirrorPacingButton = saveMirrorPacingButton,
            )
        }

        if (!mirrorCooldownInput.hasFocus()) {
            mirrorCooldownInput.setText(
                (appRule?.mirrorCooldownSecondsOverride ?: currentGlobalCooldown).toString(),
            )
        }

        updateMirrorOverrideControls(
            mirrorOverrideSwitch = mirrorOverrideSwitch,
            mirrorCooldownInput = mirrorCooldownInput,
            mirrorBurstStrategyButton = mirrorBurstStrategyButton,
            saveMirrorPacingButton = saveMirrorPacingButton,
        )
    }

    private fun updateMirrorOverrideControls(
        mirrorOverrideSwitch: SwitchMaterial,
        mirrorCooldownInput: EditText,
        mirrorBurstStrategyButton: MaterialButton,
        saveMirrorPacingButton: Button,
    ) {
        val isEnabled = mirrorOverrideSwitch.isChecked
        mirrorCooldownInput.isEnabled = isEnabled
        mirrorBurstStrategyButton.isEnabled = isEnabled
        saveMirrorPacingButton.isEnabled = true
    }

    private fun bindBurstStrategyLabel(
        button: MaterialButton,
        burstStrategy: BurstStrategy,
    ) {
        button.text = getString(
            R.string.mirror_strategy_button_template,
            burstStrategy.labelText(),
        )
    }

    private fun BurstStrategy.labelText(): String {
        return when (this) {
            BurstStrategy.LATEST_ONLY -> getString(R.string.mirror_strategy_latest_only)
            BurstStrategy.FIFO -> getString(R.string.mirror_strategy_fifo)
        }
    }

    private fun nextBurstStrategy(current: BurstStrategy): BurstStrategy {
        return when (current) {
            BurstStrategy.LATEST_ONLY -> BurstStrategy.FIFO
            BurstStrategy.FIFO -> BurstStrategy.LATEST_ONLY
        }
    }

    companion object {
        private const val ARG_PACKAGE_NAME = "package_name"

        fun newInstance(packageName: String): AppDetailFragment {
            return AppDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACKAGE_NAME, packageName)
                }
            }
        }
    }
}
