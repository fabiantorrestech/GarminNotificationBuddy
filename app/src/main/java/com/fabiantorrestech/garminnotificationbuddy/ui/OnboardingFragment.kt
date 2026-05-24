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
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.MainActivity
import com.fabiantorrestech.garminnotificationbuddy.R

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
            bindStaticStatuses(view)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { bindStaticStatuses(it) }
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

        val garminConnectInstalled = container.garminConnectStatusChecker.isGarminConnectInstalled()
        root.findViewById<TextView>(R.id.garminConnectStatusTextView).text =
            getString(
                R.string.garmin_connect_status_template,
                if (garminConnectInstalled) getString(R.string.status_ready) else getString(R.string.status_action_needed),
            )
        root.findViewById<TextView>(R.id.garminConnectSetupNoteTextView).text =
            getString(R.string.garmin_connect_setup_note)
    }
}
