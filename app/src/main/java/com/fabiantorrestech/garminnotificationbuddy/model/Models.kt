package com.fabiantorrestech.garminnotificationbuddy.model

import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

enum class DeliveryMode {
    CONNECT_IQ,
    PROXY_MIRROR,
}

enum class RuleAction {
    ALLOW,
    BLOCK,
}

enum class ScheduleScope {
    GLOBAL,
    APP,
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
)

data class DeliveryDecision(
    val shouldDeliver: Boolean,
    val reason: String,
)

data class DeliveryResult(
    val success: Boolean,
    val reason: String,
)

data class GarminSetupStatus(
    val garminConnectInstalled: Boolean = false,
    val sdkReady: Boolean = false,
    val deviceName: String? = null,
    val deviceStatus: String? = null,
    val watchAppInstalled: Boolean = false,
    val lastError: String? = null,
)

data class ScheduleWindow(
    val daysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)

fun String.toRuleAction(): RuleAction = RuleAction.valueOf(this)

fun String.toDeliveryMode(): DeliveryMode = DeliveryMode.valueOf(this)

fun String.toScheduleScope(): ScheduleScope = ScheduleScope.valueOf(this)

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
