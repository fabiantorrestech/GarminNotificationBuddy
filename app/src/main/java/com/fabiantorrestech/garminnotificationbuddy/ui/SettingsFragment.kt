package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryMode
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val masterEnabledSwitch = view.findViewById<SwitchMaterial>(R.id.masterEnableSwitch)
        val syncWithDndSwitch = view.findViewById<SwitchMaterial>(R.id.syncDndSwitch)
        val connectIqRadio = view.findViewById<MaterialRadioButton>(R.id.connectIqRadioButton)
        val proxyRadio = view.findViewById<MaterialRadioButton>(R.id.proxyMirrorRadioButton)

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
        connectIqRadio.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.setDeliveryMode(DeliveryMode.CONNECT_IQ)
            }
        }
        proxyRadio.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                container.ruleRepository.setDeliveryMode(DeliveryMode.PROXY_MIRROR)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.ruleRepository.observeGlobalSettings().collect { settings ->
                    masterEnabledSwitch.setOnCheckedChangeListener(null)
                    syncWithDndSwitch.setOnCheckedChangeListener(null)
                    masterEnabledSwitch.isChecked = settings.masterEnabled
                    syncWithDndSwitch.isChecked = settings.syncWithPhoneDnd
                    connectIqRadio.isChecked = settings.deliveryMode == DeliveryMode.CONNECT_IQ.name
                    proxyRadio.isChecked = settings.deliveryMode == DeliveryMode.PROXY_MIRROR.name

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
}
