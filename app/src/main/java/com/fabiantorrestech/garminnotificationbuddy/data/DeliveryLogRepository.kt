package com.fabiantorrestech.garminnotificationbuddy.data

import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import kotlinx.coroutines.flow.Flow

class DeliveryLogRepository(
    private val dao: BuddyDao,
) {
    fun observeLogs(): Flow<List<DeliveryLogEntity>> = dao.observeDeliveryLogs()

    suspend fun recordDecision(
        event: NotificationEvent,
        allowed: Boolean,
        reason: String,
    ) {
        dao.insertDeliveryLog(
            DeliveryLogEntity(
                packageName = event.packageName,
                appName = event.appName,
                channelId = event.channelId,
                title = event.title,
                body = event.expandedText.ifBlank { event.text },
                decision = if (allowed) "ALLOWED" else "BLOCKED",
                reason = reason,
                timestamp = event.postedAt,
            ),
        )
    }
}
