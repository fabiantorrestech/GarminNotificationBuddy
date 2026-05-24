package com.fabiantorrestech.garminnotificationbuddy.data

import android.content.pm.PackageManager
import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryMode
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import com.fabiantorrestech.garminnotificationbuddy.model.RuleAction
import com.fabiantorrestech.garminnotificationbuddy.model.ScheduleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepository(
    private val dao: BuddyDao,
    private val packageManager: PackageManager,
) {
    fun observeGlobalSettings(): Flow<GlobalSettingsEntity> {
        return dao.observeGlobalSettings().map { it ?: GlobalSettingsEntity() }
    }

    fun observeAppRules(): Flow<List<AppRuleEntity>> = dao.observeAppRules()

    fun observeAppRule(packageName: String): Flow<AppRuleEntity?> = dao.observeAppRule(packageName)

    fun observeChannelRules(packageName: String): Flow<List<ChannelRuleEntity>> {
        return dao.observeChannelRules(packageName)
    }

    fun observeKeywordRule(packageName: String): Flow<KeywordRuleEntity?> {
        return dao.observeKeywordRule(packageName)
    }

    fun observeSchedules(scope: ScheduleScope, scopeKey: String?): Flow<List<ScheduleEntity>> {
        return dao.observeSchedules(scope.name, scopeKey)
    }

    suspend fun ensureDefaults() {
        if (dao.getGlobalSettings() == null) {
            dao.upsertGlobalSettings(GlobalSettingsEntity())
        }
    }

    suspend fun getGlobalSettings(): GlobalSettingsEntity {
        return dao.getGlobalSettings() ?: GlobalSettingsEntity()
    }

    suspend fun setMasterEnabled(isEnabled: Boolean) {
        dao.upsertGlobalSettings(getGlobalSettings().copy(masterEnabled = isEnabled))
    }

    suspend fun setSyncWithPhoneDnd(syncEnabled: Boolean) {
        dao.upsertGlobalSettings(getGlobalSettings().copy(syncWithPhoneDnd = syncEnabled))
    }

    suspend fun setDeliveryMode(mode: DeliveryMode) {
        dao.upsertGlobalSettings(getGlobalSettings().copy(deliveryMode = mode.name))
    }

    suspend fun getAppRule(packageName: String): AppRuleEntity? = dao.getAppRule(packageName)

    suspend fun getChannelRule(packageName: String, channelId: String): ChannelRuleEntity? {
        return dao.getChannelRule(packageName, channelId)
    }

    suspend fun getKeywordRule(packageName: String): KeywordRuleEntity? {
        return dao.getKeywordRule(packageName)
    }

    suspend fun getSchedules(scope: ScheduleScope, scopeKey: String?): List<ScheduleEntity> {
        return dao.getSchedules(scope.name, scopeKey)
    }

    suspend fun upsertObservedEvent(event: NotificationEvent) {
        val existingAppRule = dao.getAppRule(event.packageName)
        val appLabel = resolveAppLabel(event.packageName, event.appName)
        dao.upsertAppRule(
            existingAppRule?.copy(
                appName = appLabel,
                lastSeenAt = event.postedAt,
            ) ?: AppRuleEntity(
                packageName = event.packageName,
                appName = appLabel,
                lastSeenAt = event.postedAt,
            ),
        )

        if (event.channelId.isNotBlank()) {
            val existingChannelRule = dao.getChannelRule(event.packageName, event.channelId)
            dao.upsertChannelRule(
                existingChannelRule?.copy(
                    channelName = event.channelName.ifBlank { existingChannelRule.channelName },
                    lastSeenAt = event.postedAt,
                ) ?: ChannelRuleEntity(
                    packageName = event.packageName,
                    channelId = event.channelId,
                    channelName = event.channelName.ifBlank { event.channelId },
                    lastSeenAt = event.postedAt,
                ),
            )
        }
    }

    suspend fun setAppEnabled(packageName: String, isEnabled: Boolean) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(existing.copy(isEnabled = isEnabled))
    }

    suspend fun setAppDefaultAction(packageName: String, action: RuleAction) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(existing.copy(defaultAction = action.name))
    }

    suspend fun setChannelEnabled(packageName: String, channelId: String, isEnabled: Boolean) {
        val existing = dao.getChannelRule(packageName, channelId) ?: return
        dao.upsertChannelRule(existing.copy(isEnabled = isEnabled))
    }

    suspend fun setChannelOverride(packageName: String, channelId: String, overrideAction: RuleAction?) {
        val existing = dao.getChannelRule(packageName, channelId) ?: return
        dao.upsertChannelRule(existing.copy(overrideAction = overrideAction?.name))
    }

    suspend fun saveKeywordRule(packageName: String, allowlistCsv: String, blocklistCsv: String) {
        dao.upsertKeywordRule(
            KeywordRuleEntity(
                packageName = packageName,
                allowlistCsv = allowlistCsv,
                blocklistCsv = blocklistCsv,
            ),
        )
    }

    suspend fun saveSchedule(schedule: ScheduleEntity): Long = dao.upsertSchedule(schedule)

    suspend fun deleteSchedule(scheduleId: Long) {
        dao.deleteSchedule(scheduleId)
    }

    private fun resolveAppLabel(packageName: String, fallback: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(fallback.ifBlank { packageName })
    }
}
