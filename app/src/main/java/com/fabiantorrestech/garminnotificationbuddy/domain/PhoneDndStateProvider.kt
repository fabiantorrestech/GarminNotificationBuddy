package com.fabiantorrestech.garminnotificationbuddy.domain

import android.app.NotificationManager
import android.content.Context

class PhoneDndStateProvider(context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun isDndActive(): Boolean {
        return notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun hasPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted
}
