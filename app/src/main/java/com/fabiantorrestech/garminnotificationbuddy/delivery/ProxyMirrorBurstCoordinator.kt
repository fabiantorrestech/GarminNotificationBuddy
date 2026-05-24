package com.fabiantorrestech.garminnotificationbuddy.delivery

import android.os.SystemClock
import com.fabiantorrestech.garminnotificationbuddy.data.DeliveryLogRepository
import com.fabiantorrestech.garminnotificationbuddy.data.RuleRepository
import com.fabiantorrestech.garminnotificationbuddy.model.BurstStrategy
import com.fabiantorrestech.garminnotificationbuddy.model.MirrorPacingSettings
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProxyMirrorBurstCoordinator(
    private val applicationScope: CoroutineScope,
    private val ruleRepository: RuleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val proxyMirrorDeliveryClient: ProxyMirrorDeliveryClient,
) {
    private val coordinatorMutex = Mutex()
    private val sourceStates = mutableMapOf<String, SourceState>()

    suspend fun submit(event: NotificationEvent) {
        val pacing = ruleRepository.resolveMirrorPacing(event.packageName)
        val queuedEvents = mutableListOf<NotificationEvent>()
        var deliverImmediately = false
        val sourceKey = sourceKey(event)

        coordinatorMutex.withLock {
            val state = sourceStates.getOrPut(sourceKey) { SourceState() }
            val now = SystemClock.elapsedRealtime()
            val isCoolingDown = now < state.cooldownUntilElapsedRealtime

            if (!state.isDelivering && !isCoolingDown) {
                state.isDelivering = true
                deliverImmediately = true
            } else {
                when (pacing.burstStrategy) {
                    BurstStrategy.LATEST_ONLY -> {
                        queuedEvents.addAll(state.pendingEvents.map { it.event })
                        state.pendingEvents.clear()
                        state.pendingEvents.addLast(PendingEvent(event, pacing))
                    }

                    BurstStrategy.FIFO -> {
                        state.pendingEvents.addLast(PendingEvent(event, pacing))
                    }
                }
            }
        }

        if (deliverImmediately) {
            deliverEvent(sourceKey = sourceKey, pendingEvent = PendingEvent(event, pacing))
            return
        }

        queuedEvents.forEach { replacedEvent ->
            deliveryLogRepository.recordReplaced(
                event = replacedEvent,
                reason = "replaced_pending_latest_only",
            )
        }
        deliveryLogRepository.recordQueued(
            event = event,
            reason = "queued_burst_cooldown_${pacing.burstStrategy.name.lowercase()}",
        )
    }

    private suspend fun deliverEvent(
        sourceKey: String,
        pendingEvent: PendingEvent,
    ) {
        val result = proxyMirrorDeliveryClient.deliver(pendingEvent.event)
        deliveryLogRepository.recordDeliveryResult(
            event = pendingEvent.event,
            success = result.success,
            reason = result.reason,
        )

        coordinatorMutex.withLock {
            val state = sourceStates[sourceKey]
            if (state != null) {
                state.isDelivering = false
                state.cooldownUntilElapsedRealtime = if (result.success) {
                    SystemClock.elapsedRealtime() + (pendingEvent.pacing.cooldownSeconds * 1000L)
                } else {
                    0L
                }
                scheduleNextLocked(sourceKey, state)
                cleanupLocked(sourceKey, state)
            }
        }
    }

    private fun scheduleNextLocked(sourceKey: String, state: SourceState) {
        if (state.isDelivering || state.pendingEvents.isEmpty()) {
            state.scheduledJob?.cancel()
            state.scheduledJob = null
            return
        }

        val delayMillis = (state.cooldownUntilElapsedRealtime - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)

        if (state.scheduledJob?.isActive == true && state.scheduledDelayMillis == delayMillis) {
            return
        }

        state.scheduledJob?.cancel()
        state.scheduledDelayMillis = delayMillis
        state.scheduledJob = applicationScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            dispatchPending(sourceKey)
        }
    }

    private suspend fun dispatchPending(sourceKey: String) {
        var nextPending: PendingEvent? = null

        coordinatorMutex.withLock {
            val state = sourceStates[sourceKey]
            if (state != null) {
                state.scheduledJob = null
                state.scheduledDelayMillis = null

                if (state.isDelivering || state.pendingEvents.isEmpty()) {
                    cleanupLocked(sourceKey, state)
                } else {
                    val remainingCooldown =
                        state.cooldownUntilElapsedRealtime - SystemClock.elapsedRealtime()
                    if (remainingCooldown > 0L) {
                        scheduleNextLocked(sourceKey, state)
                    } else {
                        state.isDelivering = true
                        nextPending = state.pendingEvents.removeFirst()
                    }
                }
            }
        }

        nextPending?.let { pendingEvent ->
            deliverEvent(sourceKey = sourceKey, pendingEvent = pendingEvent)
        }
    }

    private fun cleanupLocked(sourceKey: String, state: SourceState) {
        val cooldownExpired = SystemClock.elapsedRealtime() >= state.cooldownUntilElapsedRealtime
        if (!state.isDelivering && state.pendingEvents.isEmpty() && !state.hasScheduledWork() && cooldownExpired) {
            sourceStates.remove(sourceKey)
        }
    }

    private fun sourceKey(event: NotificationEvent): String {
        val channelKey = event.channelId.ifBlank { NO_CHANNEL_KEY }
        return "${event.packageName}::$channelKey"
    }

    private data class PendingEvent(
        val event: NotificationEvent,
        val pacing: MirrorPacingSettings,
    )

    private class SourceState {
        var cooldownUntilElapsedRealtime: Long = 0L
        var isDelivering: Boolean = false
        val pendingEvents = ArrayDeque<PendingEvent>()
        var scheduledJob: Job? = null
        var scheduledDelayMillis: Long? = null

        fun hasScheduledWork(): Boolean = scheduledJob?.isActive == true
    }

    companion object {
        private const val NO_CHANNEL_KEY = "__no_channel__"
    }
}
