package com.fabiantorrestech.garminnotificationbuddy.domain

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import com.fabiantorrestech.garminnotificationbuddy.model.SourceNotification

class NotificationNormalizer(
    private val packageManager: PackageManager,
    private val appPackageName: String,
) {
    fun normalize(statusBarNotification: StatusBarNotification): NotificationEvent? {
        if (statusBarNotification.packageName == appPackageName) {
            return null
        }

        val notification = statusBarNotification.notification ?: return null
        val extras = notification.extras
        val packageName = statusBarNotification.packageName
        val appName = resolveAppLabel(packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val channelId = notification.channelId.orEmpty()

        return NotificationEvent(
            packageName = packageName,
            appName = appName,
            channelId = channelId,
            channelName = channelId.ifBlank { "No channel" },
            title = title,
            text = text,
            expandedText = bigText,
            category = notification.category.orEmpty(),
            postedAt = statusBarNotification.postTime,
            sourceNotification = SourceNotification(
                key = statusBarNotification.key,
                packageName = packageName,
                tag = statusBarNotification.tag,
                notificationId = statusBarNotification.id,
            ),
        )
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }
}
