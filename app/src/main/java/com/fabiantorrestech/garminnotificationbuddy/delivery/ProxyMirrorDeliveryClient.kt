package com.fabiantorrestech.garminnotificationbuddy.delivery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryResult
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent

class ProxyMirrorDeliveryClient(
    private val context: Context,
) : WatchDeliveryClient {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.proxy_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.proxy_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override suspend fun deliver(event: NotificationEvent): DeliveryResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return DeliveryResult(false, "post_notifications_permission_missing")
        }

        val body = event.expandedText.ifBlank { event.text }.ifBlank {
            context.getString(R.string.proxy_notification_empty_body)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${event.appName}: ${event.title.ifBlank { context.getString(R.string.proxy_notification_title_fallback) }}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(45_000L)
            .build()

        notificationManager.notify(event.id.hashCode(), notification)
        return DeliveryResult(true, "delivered_proxy_mirror")
    }

    companion object {
        const val CHANNEL_ID = "buddy_forwarded_notifications"
    }
}
