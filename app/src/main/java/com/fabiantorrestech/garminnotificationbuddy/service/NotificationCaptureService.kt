package com.fabiantorrestech.garminnotificationbuddy.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.model.SourceCancellationResult
import com.fabiantorrestech.garminnotificationbuddy.model.SourceNotification
import com.fabiantorrestech.garminnotificationbuddy.processing.SourceNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val container = (application as BuddyApplication).container
        serviceScope.launch {
            container.notificationProcessor.process(
                statusBarNotification = sbn,
                sourceNotificationController = SourceNotificationController(::cancelSourceNotification),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun cancelSourceNotification(sourceNotification: SourceNotification): SourceCancellationResult {
        return runCatching {
            cancelNotification(sourceNotification.key)
            SourceCancellationResult(
                success = true,
                reason = "source_canceled",
            )
        }.getOrElse { error ->
            SourceCancellationResult(
                success = false,
                reason = "source_cancel_failed_${error.javaClass.simpleName.lowercase()}",
            )
        }
    }
}
