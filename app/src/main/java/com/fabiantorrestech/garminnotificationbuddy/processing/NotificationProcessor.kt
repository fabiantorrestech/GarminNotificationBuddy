package com.fabiantorrestech.garminnotificationbuddy.processing

import com.fabiantorrestech.garminnotificationbuddy.data.DeliveryLogRepository
import com.fabiantorrestech.garminnotificationbuddy.data.RuleRepository
import com.fabiantorrestech.garminnotificationbuddy.delivery.ConnectIqDeliveryClient
import com.fabiantorrestech.garminnotificationbuddy.delivery.ProxyMirrorDeliveryClient
import com.fabiantorrestech.garminnotificationbuddy.domain.DecisionEngine
import com.fabiantorrestech.garminnotificationbuddy.domain.NotificationNormalizer
import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryMode
import com.fabiantorrestech.garminnotificationbuddy.model.toDeliveryMode
import android.service.notification.StatusBarNotification

class NotificationProcessor(
    private val ruleRepository: RuleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val decisionEngine: DecisionEngine,
    private val normalizer: NotificationNormalizer,
    private val connectIqDeliveryClient: ConnectIqDeliveryClient,
    private val proxyMirrorDeliveryClient: ProxyMirrorDeliveryClient,
) {
    suspend fun process(statusBarNotification: StatusBarNotification) {
        val event = normalizer.normalize(statusBarNotification) ?: return

        ruleRepository.upsertObservedEvent(event)

        val decision = decisionEngine.evaluate(event)
        if (!decision.shouldDeliver) {
            deliveryLogRepository.recordDecision(event, allowed = false, reason = decision.reason)
            return
        }

        val deliveryMode = ruleRepository.getGlobalSettings().deliveryMode.toDeliveryMode()
        val deliveryResult = when (deliveryMode) {
            DeliveryMode.CONNECT_IQ -> connectIqDeliveryClient.deliver(event)
            DeliveryMode.PROXY_MIRROR -> proxyMirrorDeliveryClient.deliver(event)
        }

        deliveryLogRepository.recordDecision(
            event = event,
            allowed = deliveryResult.success,
            reason = deliveryResult.reason,
        )
    }
}
