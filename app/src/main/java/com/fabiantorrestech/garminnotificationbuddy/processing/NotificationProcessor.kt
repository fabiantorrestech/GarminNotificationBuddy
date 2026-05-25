package com.fabiantorrestech.garminnotificationbuddy.processing

import android.service.notification.StatusBarNotification
import com.fabiantorrestech.garminnotificationbuddy.data.DeliveryLogRepository
import com.fabiantorrestech.garminnotificationbuddy.data.RuleRepository
import com.fabiantorrestech.garminnotificationbuddy.delivery.ProxyMirrorBurstCoordinator
import com.fabiantorrestech.garminnotificationbuddy.domain.DecisionEngine
import com.fabiantorrestech.garminnotificationbuddy.domain.NotificationNormalizer
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationProcessResult
import com.fabiantorrestech.garminnotificationbuddy.model.SourceCancellationStatus

class NotificationProcessor(
    private val ruleRepository: RuleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val decisionEngine: DecisionEngine,
    private val normalizer: NotificationNormalizer,
    private val proxyMirrorBurstCoordinator: ProxyMirrorBurstCoordinator,
) {
    suspend fun process(
        statusBarNotification: StatusBarNotification,
        sourceNotificationController: SourceNotificationController,
    ): NotificationProcessResult? {
        val event = normalizer.normalize(statusBarNotification) ?: return null

        ruleRepository.upsertObservedEvent(event)

        val decision = decisionEngine.evaluate(event)
        if (!decision.shouldDeliver) {
            deliveryLogRepository.recordBlockedDecision(event, reason = decision.reason)
            return NotificationProcessResult(decision = decision)
        }

        val sourceCancellationResult = sourceNotificationController.cancel(event.sourceNotification)
        val deliveryEvent = event.copy(
            sourceCancellationStatus = if (sourceCancellationResult.success) {
                SourceCancellationStatus.SUCCEEDED
            } else {
                SourceCancellationStatus.FAILED
            },
        )
        val mirrorDispatchResult = proxyMirrorBurstCoordinator.submit(deliveryEvent)
        return NotificationProcessResult(
            decision = decision,
            sourceCancellationResult = sourceCancellationResult,
            mirrorDispatchResult = mirrorDispatchResult,
        )
    }
}
