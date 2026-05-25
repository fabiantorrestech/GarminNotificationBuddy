package com.fabiantorrestech.garminnotificationbuddy.processing

import com.fabiantorrestech.garminnotificationbuddy.model.SourceCancellationResult
import com.fabiantorrestech.garminnotificationbuddy.model.SourceNotification

fun interface SourceNotificationController {
    fun cancel(sourceNotification: SourceNotification): SourceCancellationResult
}
