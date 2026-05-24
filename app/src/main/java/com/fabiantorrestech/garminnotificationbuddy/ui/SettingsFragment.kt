package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.data.GlobalSettingsEntity
import com.fabiantorrestech.garminnotificationbuddy.model.BurstStrategy
import com.fabiantorrestech.garminnotificationbuddy.model.toBurstStrategyOrDefault
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val masterEnabledSwitch = view.findViewById<SwitchMaterial>(R.id.masterEnableSwitch)
        val syncWithDndSwitch = view.findViewById<SwitchMaterial>(R.id.syncDndSwitch)
        val mirrorCooldownInput = view.findViewById<EditText>(R.id.mirrorCooldownEditText)
        val mirrorBurstStrategyButton =
            view.findViewById<MaterialButton>(R.id.mirrorBurstStrategyButton)
        val saveMirrorPacingButton = view.findViewById<Button>(R.id.saveMirrorPacingButton)

        var selectedBurstStrategy = BurstStrategy.LATEST_ONLY
        var currentSettings = GlobalSettingsEntity()

        masterEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.setMasterEnabled(isChecked)
            }
        }
        syncWithDndSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.setSyncWithPhoneDnd(isChecked)
            }
        }
        mirrorBurstStrategyButton.setOnClickListener {
            selectedBurstStrategy = nextBurstStrategy(selectedBurstStrategy)
            bindBurstStrategyLabel(mirrorBurstStrategyButton, selectedBurstStrategy)
        }
        saveMirrorPacingButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.saveMirrorPacingSettings(
                    cooldownSeconds = mirrorCooldownInput.text.toString()
                        .toIntOrNull()
                        ?.coerceAtLeast(0)
                        ?: currentSettings.mirrorCooldownSeconds,
                    burstStrategy = selectedBurstStrategy,
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.ruleRepository.observeGlobalSettings().collect { settings ->
                    currentSettings = settings

                    masterEnabledSwitch.setOnCheckedChangeListener(null)
                    syncWithDndSwitch.setOnCheckedChangeListener(null)
                    masterEnabledSwitch.isChecked = settings.masterEnabled
                    syncWithDndSwitch.isChecked = settings.syncWithPhoneDnd

                    if (!mirrorCooldownInput.hasFocus()) {
                        mirrorCooldownInput.setText(settings.mirrorCooldownSeconds.toString())
                    }

                    selectedBurstStrategy = settings.mirrorBurstStrategy.toBurstStrategyOrDefault()
                    bindBurstStrategyLabel(mirrorBurstStrategyButton, selectedBurstStrategy)

                    masterEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            container.ruleRepository.setMasterEnabled(isChecked)
                        }
                    }
                    syncWithDndSwitch.setOnCheckedChangeListener { _, isChecked ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            container.ruleRepository.setSyncWithPhoneDnd(isChecked)
                        }
                    }
                }
            }
        }
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
}
