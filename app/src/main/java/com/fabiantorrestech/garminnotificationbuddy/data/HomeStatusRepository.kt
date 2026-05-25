package com.fabiantorrestech.garminnotificationbuddy.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fabiantorrestech.garminnotificationbuddy.delivery.GarminConnectStatusChecker
import com.fabiantorrestech.garminnotificationbuddy.domain.PhoneDndStateProvider
import com.fabiantorrestech.garminnotificationbuddy.domain.ScheduleEvaluator
import com.fabiantorrestech.garminnotificationbuddy.model.HomeSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Clock
import java.time.ZonedDateTime

class HomeStatusRepository(
    private val context: Context,
    private val ruleRepository: RuleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val garminConnectStatusChecker: GarminConnectStatusChecker,
    private val phoneDndStateProvider: PhoneDndStateProvider,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeSummary(): Flow<HomeSummary> {
        return combine(
            ruleRepository.observeGlobalSettings(),
            ruleRepository.observeScheduleListItems(),
            deliveryLogRepository.observeLatestDeliveredTimestamp(),
            minuteTicker(),
        ) { settings, schedules, lastDeliveredTimestamp, nowMillis ->
            SummaryInputs(
                settings = settings,
                schedules = schedules,
                lastDeliveredTimestamp = lastDeliveredTimestamp,
                nowMillis = nowMillis,
            )
        }.map { inputs ->
            val now = ZonedDateTime.now(clock)
            val activeSchedules = scheduleEvaluator.activeSchedules(
                inputs.schedules.map { it.schedule },
                now,
            )
            val hasDndAccess = phoneDndStateProvider.hasPolicyAccess()
            val phoneDndActive = hasDndAccess && phoneDndStateProvider.isDndActive()
            val activeScheduleIds = activeSchedules.map { it.id }.toSet()

            HomeSummary(
                notificationListenerGranted = isNotificationListenerGranted(),
                postNotificationsGranted = arePostNotificationsGranted(),
                garminConnectInstalled = garminConnectStatusChecker.isGarminConnectInstalled(),
                lastBuddyRepostTimestamp = inputs.lastDeliveredTimestamp,
                deliveredInLast24Hours = deliveryLogRepository.countDeliveredSince(
                    inputs.nowMillis - LAST_24_HOURS_MILLIS,
                ),
                enabledScheduleCount = inputs.schedules.count { it.schedule.isEnabled },
                activeScheduleCount = activeSchedules.size,
                activeGlobalScheduleCount = inputs.schedules.count { scheduleItem ->
                    scheduleItem.assignedAppCount == 0 && activeScheduleIds.contains(scheduleItem.schedule.id)
                },
                activeAppScheduleCount = inputs.schedules.count { scheduleItem ->
                    scheduleItem.assignedAppCount > 0 && activeScheduleIds.contains(scheduleItem.schedule.id)
                },
                followPhoneDndRules = inputs.settings.followPhoneDndRules,
                dndPolicyAccessGranted = hasDndAccess,
                phoneDndActive = phoneDndActive,
            )
        }
    }

    private fun isNotificationListenerGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    private fun arePostNotificationsGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun minuteTicker(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            val nowMillis = System.currentTimeMillis()
            emit(nowMillis)
            val delayMillis = (ONE_MINUTE_MILLIS - (nowMillis % ONE_MINUTE_MILLIS))
                .coerceIn(1L, ONE_MINUTE_MILLIS)
            delay(delayMillis)
        }
    }

    private data class SummaryInputs(
        val settings: GlobalSettingsEntity,
        val schedules: List<ScheduleListItem>,
        val lastDeliveredTimestamp: Long?,
        val nowMillis: Long,
    )

    companion object {
        private const val ONE_MINUTE_MILLIS = 60_000L
        private const val LAST_24_HOURS_MILLIS = 24 * 60 * 60 * 1000L
    }
}
