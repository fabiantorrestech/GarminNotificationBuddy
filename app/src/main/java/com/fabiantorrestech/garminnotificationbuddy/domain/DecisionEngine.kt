package com.fabiantorrestech.garminnotificationbuddy.domain

import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryDecision
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import com.fabiantorrestech.garminnotificationbuddy.model.RuleAction
import com.fabiantorrestech.garminnotificationbuddy.model.ScheduleScope
import com.fabiantorrestech.garminnotificationbuddy.model.searchableText
import com.fabiantorrestech.garminnotificationbuddy.model.toRuleAction
import com.fabiantorrestech.garminnotificationbuddy.data.RuleRepository
import java.time.ZonedDateTime
import java.util.Locale

class DecisionEngine(
    private val ruleRepository: RuleRepository,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val phoneDndStateProvider: PhoneDndStateProvider,
) {
    suspend fun evaluate(
        event: NotificationEvent,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): DeliveryDecision {
        val globalSettings = ruleRepository.getGlobalSettings()
        if (!globalSettings.masterEnabled) {
            return DeliveryDecision(false, "master_disabled")
        }

        if (globalSettings.syncWithPhoneDnd && phoneDndStateProvider.isDndActive()) {
            return DeliveryDecision(false, "phone_dnd_active")
        }

        val globalSchedules = ruleRepository.getSchedules(ScheduleScope.GLOBAL, null)
        if (scheduleEvaluator.anyActiveBlockingSchedule(globalSchedules, now)) {
            return DeliveryDecision(false, "global_schedule_block")
        }

        val appRule = ruleRepository.getAppRule(event.packageName)
            ?: return DeliveryDecision(false, "app_not_enabled")
        if (!appRule.isEnabled) {
            return DeliveryDecision(false, "app_disabled")
        }

        val appSchedules = ruleRepository.getSchedules(ScheduleScope.APP, event.packageName)
        if (scheduleEvaluator.anyActiveBlockingSchedule(appSchedules, now)) {
            return DeliveryDecision(false, "app_schedule_block")
        }

        val channelRule = if (event.channelId.isNotBlank()) {
            ruleRepository.getChannelRule(event.packageName, event.channelId)
        } else {
            null
        }
        if (channelRule != null && !channelRule.isEnabled) {
            return DeliveryDecision(false, "channel_disabled")
        }

        val keywords = ruleRepository.getKeywordRule(event.packageName)
        val normalizedText = event.searchableText()
        val blockTerms = parseTerms(keywords?.blocklistCsv)
        if (blockTerms.any { normalizedText.contains(it) }) {
            return DeliveryDecision(false, "keyword_block")
        }

        val allowTerms = parseTerms(keywords?.allowlistCsv)
        if (allowTerms.isNotEmpty() && allowTerms.none { normalizedText.contains(it) }) {
            return DeliveryDecision(false, "keyword_allowlist_miss")
        }

        val resolvedAction = channelRule?.overrideAction?.toRuleAction()
            ?: appRule.defaultAction.toRuleAction()

        return if (resolvedAction == RuleAction.ALLOW) {
            DeliveryDecision(true, "allowed_by_rules")
        } else {
            DeliveryDecision(false, "app_default_block")
        }
    }

    private fun parseTerms(csv: String?): List<String> {
        return csv.orEmpty()
            .split(',')
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
    }
}
