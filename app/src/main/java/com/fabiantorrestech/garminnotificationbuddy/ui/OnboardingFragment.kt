package com.fabiantorrestech.garminnotificationbuddy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.MainActivity
import com.fabiantorrestech.garminnotificationbuddy.R
import kotlinx.coroutines.launch

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.notificationListenerButton).setOnClickListener {
            (requireActivity() as MainActivity).openNotificationListenerSettings()
        }
        view.findViewById<Button>(R.id.notificationPermissionButton).setOnClickListener {
            (requireActivity() as MainActivity).requestPostNotificationsPermission()
        }
        view.findViewById<Button>(R.id.dndButton).setOnClickListener {
            (requireActivity() as MainActivity).openNotificationPolicyAccessSettings()
        }
        view.findViewById<Button>(R.id.refreshGarminStatusButton).setOnClickListener {
            container.connectIqDeliveryClient.refreshSetupStatus()
            bindStaticStatuses(view)
        }
        view.findViewById<Button>(R.id.openGarminStoreButton).setOnClickListener {
            container.connectIqDeliveryClient.openStore()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    container.ruleRepository.observeGlobalSettings().collect { settings ->
                        view.findViewById<TextView>(R.id.deliveryModeStatusTextView).text =
                            getString(R.string.delivery_mode_status_template, settings.deliveryMode)
                    }
                }
                launch {
                    container.connectIqDeliveryClient.setupStatus.collect { status ->
                        view.findViewById<TextView>(R.id.garminConnectStatusTextView).text =
                            getString(
                                R.string.garmin_connect_status_template,
                                if (status.garminConnectInstalled) getString(R.string.status_ready) else getString(R.string.status_action_needed),
                            )
                        val connectIqSummary = buildString {
                            append(
                                getString(
                                    R.string.connect_iq_sdk_status_template,
                                    if (status.sdkReady) getString(R.string.status_ready) else getString(R.string.status_waiting),
                                ),
                            )
                            status.deviceName?.let { append("\nDevice: $it (${status.deviceStatus ?: "Unknown"})") }
                            append("\nWatch app: ${if (status.watchAppInstalled) getString(R.string.status_ready) else getString(R.string.status_action_needed)}")
                            status.lastError?.let { append("\nLast issue: $it") }
                        }
                        view.findViewById<TextView>(R.id.connectIqStatusTextView).text = connectIqSummary
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { bindStaticStatuses(it) }
        container.connectIqDeliveryClient.refreshSetupStatus()
    }

    private fun bindStaticStatuses(root: View) {
        val listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
        root.findViewById<TextView>(R.id.notificationListenerStatusTextView).text =
            getString(
                R.string.notification_listener_status_template,
                if (listenerGranted) getString(R.string.status_ready) else getString(R.string.status_action_needed),
            )

        val postNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        root.findViewById<TextView>(R.id.postNotificationsStatusTextView).text =
            getString(
                R.string.post_notifications_status_template,
                if (postNotificationsGranted) getString(R.string.status_ready) else getString(R.string.status_action_needed),
            )

        root.findViewById<TextView>(R.id.dndStatusTextView).text =
            getString(
                R.string.dnd_status_template,
                if (container.phoneDndStateProvider.hasPolicyAccess()) getString(R.string.status_ready) else getString(R.string.status_optional),
            )
    }
}
