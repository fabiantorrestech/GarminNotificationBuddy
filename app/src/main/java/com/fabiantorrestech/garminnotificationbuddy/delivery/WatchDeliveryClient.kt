package com.fabiantorrestech.garminnotificationbuddy.delivery

import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryResult
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent

interface WatchDeliveryClient {
    suspend fun deliver(event: NotificationEvent): DeliveryResult
}
