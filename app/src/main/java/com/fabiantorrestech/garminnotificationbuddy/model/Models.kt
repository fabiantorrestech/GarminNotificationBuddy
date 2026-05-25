package com.fabiantorrestech.garminnotificationbuddy.model

import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

enum class BurstStrategy {
    LATEST_ONLY,
    FIFO,
}

enum class RuleAction {
    ALLOW,
    BLOCK,
}

data class NotificationEvent(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val text: String,
    val expandedText: String,
    val category: String,
    val postedAt: Long,
    val sourceNotification: SourceNotification,
    val sourceCancellationStatus: SourceCancellationStatus = SourceCancellationStatus.NOT_ATTEMPTED,
)

data class SourceNotification(
    val key: String,
    val packageName: String,
    val tag: String?,
    val notificationId: Int,
)

enum class SourceCancellationStatus {
    NOT_ATTEMPTED,
    SUCCEEDED,
    FAILED,
}

data class DeliveryDecision(
    val shouldDeliver: Boolean,
    val reason: String,
)

data class DeliveryResult(
    val success: Boolean,
    val reason: String,
)

enum class MirrorDispatchState {
    DELIVERED,
    QUEUED,
}

data class MirrorDispatchResult(
    val state: MirrorDispatchState,
    val reason: String,
    val deliveryResult: DeliveryResult? = null,
)

data class SourceCancellationResult(
    val success: Boolean,
    val reason: String,
)

data class NotificationProcessResult(
    val decision: DeliveryDecision,
    val sourceCancellationResult: SourceCancellationResult? = null,
    val mirrorDispatchResult: MirrorDispatchResult? = null,
)

data class MirrorPacingSettings(
    val cooldownMillis: Int,
    val burstStrategy: BurstStrategy,
)

data class HomeSummary(
    val notificationListenerGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val garminConnectInstalled: Boolean,
    val lastBuddyRepostTimestamp: Long?,
    val deliveredInLast24Hours: Int,
    val enabledScheduleCount: Int,
    val activeScheduleCount: Int,
    val activeGlobalScheduleCount: Int,
    val activeAppScheduleCount: Int,
    val followPhoneDndRules: Boolean,
    val dndPolicyAccessGranted: Boolean,
    val phoneDndActive: Boolean,
) {
    val buddyReady: Boolean
        get() = notificationListenerGranted && postNotificationsGranted && garminConnectInstalled

    val buddyDndBlockActive: Boolean
        get() = !followPhoneDndRules && dndPolicyAccessGranted && phoneDndActive
}

data class ScheduleWindow(
    val daysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)

fun String.toRuleAction(): RuleAction = RuleAction.valueOf(this)

fun String?.toBurstStrategyOrDefault(
    default: BurstStrategy = BurstStrategy.LATEST_ONLY,
): BurstStrategy {
    return runCatching { BurstStrategy.valueOf(this.orEmpty()) }
        .getOrDefault(default)
}

fun NotificationEvent.searchableText(): String {
    return buildString {
        append(title)
        append(' ')
        append(text)
        append(' ')
        append(expandedText)
    }.lowercase(Locale.US)
}

fun ZonedDateTime.minuteOfDay(): Int = hour * 60 + minute
