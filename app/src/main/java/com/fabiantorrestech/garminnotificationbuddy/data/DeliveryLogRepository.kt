package com.fabiantorrestech.garminnotificationbuddy.data

import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import kotlinx.coroutines.flow.Flow

class DeliveryLogRepository(
    private val dao: BuddyDao,
) {
    fun observeLogs(): Flow<List<DeliveryLogEntity>> = dao.observeDeliveryLogs()

    fun observeLatestDeliveredTimestamp(): Flow<Long?> = dao.observeLatestDeliveredTimestamp()

    suspend fun countDeliveredSince(cutoffTimestamp: Long): Int {
        return dao.countDeliveredSince(cutoffTimestamp)
    }

    suspend fun recordBlockedDecision(
        event: NotificationEvent,
        reason: String,
    ) {
        insertLog(event = event, decision = "BLOCKED", reason = reason)
    }

    suspend fun recordDeliveryResult(
        event: NotificationEvent,
        success: Boolean,
        reason: String,
    ) {
        insertLog(
            event = event,
            decision = if (success) "DELIVERED" else "FAILED",
            reason = reason,
        )
    }

    suspend fun recordQueued(
        event: NotificationEvent,
        reason: String,
    ) {
        insertLog(event = event, decision = "QUEUED", reason = reason)
    }

    suspend fun recordReplaced(
        event: NotificationEvent,
        reason: String,
    ) {
        insertLog(event = event, decision = "REPLACED", reason = reason)
    }

    private suspend fun insertLog(
        event: NotificationEvent,
        decision: String,
        reason: String,
    ) {
        dao.insertDeliveryLog(
            DeliveryLogEntity(
                packageName = event.packageName,
                appName = event.appName,
                channelId = event.channelId,
                title = event.title,
                body = event.expandedText.ifBlank { event.text },
                decision = decision,
                reason = reason,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }
}
