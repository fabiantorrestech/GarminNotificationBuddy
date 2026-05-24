package com.fabiantorrestech.garminnotificationbuddy.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
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
            container.notificationProcessor.process(sbn)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
