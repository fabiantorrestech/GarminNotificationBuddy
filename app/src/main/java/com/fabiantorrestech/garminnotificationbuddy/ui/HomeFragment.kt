package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.model.HomeSummary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = HomeViews(
            buddyStatusValue = view.findViewById(R.id.buddyStatusValueTextView),
            buddyStatusDetail = view.findViewById(R.id.buddyStatusDetailTextView),
            garminStatusValue = view.findViewById(R.id.garminStatusValueTextView),
            garminStatusDetail = view.findViewById(R.id.garminStatusDetailTextView),
            lastRepostValue = view.findViewById(R.id.lastRepostValueTextView),
            lastRepostDetail = view.findViewById(R.id.lastRepostDetailTextView),
            pushed24hValue = view.findViewById(R.id.pushed24hValueTextView),
            pushed24hDetail = view.findViewById(R.id.pushed24hDetailTextView),
            schedulesValue = view.findViewById(R.id.schedulesValueTextView),
            schedulesDetail = view.findViewById(R.id.schedulesDetailTextView),
            dndValue = view.findViewById(R.id.dndValueTextView),
            dndDetail = view.findViewById(R.id.dndDetailTextView),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.homeStatusRepository.observeSummary().collect { summary ->
                    bindSummary(binding, summary)
                }
            }
        }
    }

    private fun bindSummary(views: HomeViews, summary: HomeSummary) {
        views.buddyStatusValue.text = getString(
            if (summary.buddyReady) R.string.home_value_ready else R.string.home_value_action_needed,
        )
        views.buddyStatusDetail.text = if (summary.buddyReady) {
            getString(R.string.home_buddy_status_ready_detail)
        } else {
            getString(
                R.string.home_buddy_status_missing_template,
                buildMissingRequirements(summary),
            )
        }

        views.garminStatusValue.text = getString(
            if (summary.garminConnectInstalled) R.string.home_value_installed else R.string.home_value_missing,
        )
        views.garminStatusDetail.text = getString(
            if (summary.garminConnectInstalled) {
                R.string.home_garmin_installed_detail
            } else {
                R.string.home_garmin_missing_detail
            },
        )

        views.lastRepostValue.text = summary.lastBuddyRepostTimestamp?.let { formatTimestamp(it) }
            ?: getString(R.string.home_value_never)
        views.lastRepostDetail.text = getString(
            if (summary.lastBuddyRepostTimestamp == null) {
                R.string.home_last_repost_empty_detail
            } else {
                R.string.home_last_repost_ready_detail
            },
        )

        views.pushed24hValue.text = summary.deliveredInLast24Hours.toString()
        views.pushed24hDetail.text = getString(R.string.home_pushed_24h_detail)

        views.schedulesValue.text = getString(
            if (summary.activeScheduleCount > 0) R.string.home_value_active else R.string.home_value_inactive,
        )
        views.schedulesDetail.text = buildScheduleSummary(summary)

        views.dndValue.text = getString(
            if (summary.buddyDndBlockActive) R.string.home_value_active else R.string.home_value_inactive,
        )
        views.dndDetail.text = buildDndSummary(summary)
    }

    private fun buildMissingRequirements(summary: HomeSummary): String {
        return listOfNotNull(
            if (!summary.notificationListenerGranted) {
                getString(R.string.home_missing_listener)
            } else {
                null
            },
            if (!summary.postNotificationsGranted) {
                getString(R.string.home_missing_post_notifications)
            } else {
                null
            },
            if (!summary.garminConnectInstalled) {
                getString(R.string.home_missing_garmin_connect)
            } else {
                null
            },
        ).joinToString(", ")
    }

    private fun buildScheduleSummary(summary: HomeSummary): String {
        if (summary.enabledScheduleCount == 0) {
            return getString(R.string.home_schedules_none_enabled)
        }

        val headline = if (summary.activeScheduleCount == 0) {
            getString(
                R.string.home_schedules_inactive_template,
                summary.enabledScheduleCount,
            )
        } else {
            getString(
                R.string.home_schedules_active_template,
                summary.activeScheduleCount,
                summary.enabledScheduleCount,
            )
        }

        val breakdown = getString(
            R.string.home_schedules_breakdown_template,
            summary.activeGlobalScheduleCount,
            summary.activeAppScheduleCount,
        )
        return "$headline $breakdown"
    }

    private fun buildDndSummary(summary: HomeSummary): String {
        return when {
            summary.buddyDndBlockActive -> getString(R.string.home_dnd_active_detail)
            summary.followPhoneDndRules -> getString(R.string.home_dnd_follow_rules_detail)
            !summary.dndPolicyAccessGranted -> getString(R.string.home_dnd_access_missing_detail)
            else -> getString(R.string.home_dnd_inactive_detail)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(timestamp))
    }

    private data class HomeViews(
        val buddyStatusValue: TextView,
        val buddyStatusDetail: TextView,
        val garminStatusValue: TextView,
        val garminStatusDetail: TextView,
        val lastRepostValue: TextView,
        val lastRepostDetail: TextView,
        val pushed24hValue: TextView,
        val pushed24hDetail: TextView,
        val schedulesValue: TextView,
        val schedulesDetail: TextView,
        val dndValue: TextView,
        val dndDetail: TextView,
    )
}
