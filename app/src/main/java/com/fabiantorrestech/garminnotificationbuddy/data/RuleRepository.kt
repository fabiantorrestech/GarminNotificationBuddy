package com.fabiantorrestech.garminnotificationbuddy.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.fabiantorrestech.garminnotificationbuddy.model.BurstStrategy
import com.fabiantorrestech.garminnotificationbuddy.model.MirrorPacingSettings
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import com.fabiantorrestech.garminnotificationbuddy.model.RuleAction
import com.fabiantorrestech.garminnotificationbuddy.model.toBurstStrategyOrDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

data class AppListItem(
    val packageName: String,
    val appName: String,
    val lastSeenAt: Long,
    val isEnabled: Boolean,
    val isInstalled: Boolean,
    val isSystemApp: Boolean,
    val keepVisibleInUserApps: Boolean,
)

data class ScheduleListItem(
    val schedule: ScheduleEntity,
    val assignedAppCount: Int,
)

enum class ManualPackageAddResult {
    ADDED,
    ALREADY_EXISTS,
    INVALID,
}

class RuleRepository(
    private val dao: BuddyDao,
    private val packageManager: PackageManager,
) {
    fun observeGlobalSettings(): Flow<GlobalSettingsEntity> {
        return dao.observeGlobalSettings().map { it ?: GlobalSettingsEntity() }
    }

    fun observeAppRules(): Flow<List<AppRuleEntity>> = dao.observeAppRules()

    fun observeAppListItems(): Flow<List<AppListItem>> {
        return dao.observeAppRules().map { rules ->
            buildComprehensiveAppListItems(rules)
        }
    }

    fun observeAppRule(packageName: String): Flow<AppRuleEntity?> = dao.observeAppRule(packageName)

    fun observeChannelRules(packageName: String): Flow<List<ChannelRuleEntity>> {
        return dao.observeChannelRules(packageName)
    }

    fun observeKeywordRule(packageName: String): Flow<KeywordRuleEntity?> {
        return dao.observeKeywordRule(packageName)
    }

    fun observeScheduleListItems(): Flow<List<ScheduleListItem>> {
        return dao.observeScheduleListItems().map { items ->
            items.map { ScheduleListItem(schedule = it.schedule, assignedAppCount = it.assignedAppCount) }
        }
    }

    fun observeSchedulesForApp(packageName: String): Flow<List<ScheduleListItem>> {
        return dao.observeSchedulesForApp(packageName).map { items ->
            items.map { ScheduleListItem(schedule = it.schedule, assignedAppCount = it.assignedAppCount) }
        }
    }

    fun observeSchedule(scheduleId: Long): Flow<ScheduleEntity?> = dao.observeSchedule(scheduleId)

    fun observeScheduleAssignments(scheduleId: Long): Flow<Set<String>> {
        return dao.observeScheduleAssignments(scheduleId).map { it.toSet() }
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

    suspend fun setFollowPhoneDndRules(followRules: Boolean) {
        dao.upsertGlobalSettings(getGlobalSettings().copy(followPhoneDndRules = followRules))
    }

    suspend fun saveMirrorPacingSettings(cooldownMillis: Int, burstStrategy: BurstStrategy) {
        dao.upsertGlobalSettings(
            getGlobalSettings().copy(
                mirrorCooldownMillis = cooldownMillis.coerceAtLeast(0),
                mirrorBurstStrategy = burstStrategy.name,
            ),
        )
    }

    suspend fun resolveMirrorPacing(packageName: String): MirrorPacingSettings {
        val globalSettings = getGlobalSettings()
        val appRule = dao.getAppRule(packageName)
        val globalStrategy = globalSettings.mirrorBurstStrategy.toBurstStrategyOrDefault()
        return MirrorPacingSettings(
            cooldownMillis = appRule?.mirrorCooldownMillisOverride
                ?: globalSettings.mirrorCooldownMillis,
            burstStrategy = appRule?.mirrorBurstStrategyOverride
                .toBurstStrategyOrDefault(globalStrategy),
        )
    }

    suspend fun getAppRule(packageName: String): AppRuleEntity? = dao.getAppRule(packageName)

    suspend fun getChannelRule(packageName: String, channelId: String): ChannelRuleEntity? {
        return dao.getChannelRule(packageName, channelId)
    }

    suspend fun getKeywordRule(packageName: String): KeywordRuleEntity? {
        return dao.getKeywordRule(packageName)
    }

    suspend fun getGlobalSchedules(): List<ScheduleEntity> = dao.getGlobalSchedules()

    suspend fun getSchedulesForApp(packageName: String): List<ScheduleEntity> = dao.getSchedulesForApp(packageName)

    suspend fun getSchedule(scheduleId: Long): ScheduleEntity? = dao.getSchedule(scheduleId)

    suspend fun getScheduleAssignments(scheduleId: Long): Set<String> {
        return dao.getScheduleAssignments(scheduleId).toSet()
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

    suspend fun refreshAppList() = withContext(Dispatchers.IO) {
        val existingRulesByPackage = dao.getAllAppRules().associateBy { it.packageName }
        val discoveredRules = queryInstalledApps()
            .mapNotNull { discovered ->
                val existing = existingRulesByPackage[discovered.packageName]
                if (existing == null) {
                    AppRuleEntity(
                        packageName = discovered.packageName,
                        appName = discovered.appName,
                    )
                } else if (existing.appName != discovered.appName) {
                    existing.copy(appName = discovered.appName)
                } else {
                    null
                }
            }

        if (discoveredRules.isNotEmpty()) {
            dao.upsertAppRules(discoveredRules)
        }
    }

    suspend fun addManualPackage(packageNameInput: String): ManualPackageAddResult = withContext(Dispatchers.IO) {
        val packageName = packageNameInput.trim()
        if (packageName.isBlank()) {
            return@withContext ManualPackageAddResult.INVALID
        }

        if (dao.getAppRule(packageName) != null) {
            return@withContext ManualPackageAddResult.ALREADY_EXISTS
        }

        dao.upsertAppRule(
            AppRuleEntity(
                packageName = packageName,
                appName = resolveAppLabel(packageName, packageName),
                isPinned = true,
            ),
        )
        ManualPackageAddResult.ADDED
    }

    suspend fun setAppEnabled(packageName: String, isEnabled: Boolean) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(
            existing.copy(
                isEnabled = isEnabled,
                defaultAction = if (isEnabled) RuleAction.ALLOW.name else RuleAction.BLOCK.name,
            ),
        )
    }

    suspend fun setAppDefaultAction(packageName: String, action: RuleAction) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(
            existing.copy(
                isEnabled = action == RuleAction.ALLOW,
                defaultAction = action.name,
            ),
        )
    }

    suspend fun saveAppMirrorPacingOverride(
        packageName: String,
        isOverrideEnabled: Boolean,
        cooldownMillis: Int?,
        burstStrategy: BurstStrategy?,
    ) {
        val existing = dao.getAppRule(packageName) ?: return
        dao.upsertAppRule(
            existing.copy(
                mirrorCooldownMillisOverride = if (isOverrideEnabled) {
                    cooldownMillis?.coerceAtLeast(0)
                } else {
                    null
                },
                mirrorBurstStrategyOverride = if (isOverrideEnabled) {
                    burstStrategy?.name
                } else {
                    null
                },
            ),
        )
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

    suspend fun saveSchedule(
        schedule: ScheduleEntity,
        assignedPackages: Set<String>,
    ): Long {
        val scheduleId = dao.upsertSchedule(schedule)
        dao.deleteScheduleAssignments(scheduleId)
        if (assignedPackages.isNotEmpty()) {
            dao.upsertScheduleAssignments(
                assignedPackages.sorted().map { packageName ->
                    ScheduleAppAssignmentEntity(
                        scheduleId = scheduleId,
                        packageName = packageName,
                    )
                },
            )
        }
        return scheduleId
    }

    suspend fun deleteSchedule(scheduleId: Long) {
        dao.deleteSchedule(scheduleId)
    }

    private suspend fun shouldShowInAppList(rule: AppRuleEntity, isInstalled: Boolean): Boolean {
        if (isInstalled) {
            return true
        }

        if (
            rule.isPinned ||
            rule.lastSeenAt > 0L ||
            rule.isEnabled ||
            rule.defaultAction != RuleAction.BLOCK.name ||
            rule.mirrorCooldownMillisOverride != null ||
            rule.mirrorBurstStrategyOverride != null
        ) {
            return true
        }

        return dao.hasKeywordRule(rule.packageName) ||
            dao.hasChannelRules(rule.packageName) ||
            dao.hasAppSchedules(rule.packageName)
    }

    private suspend fun shouldKeepVisibleInUserApps(rule: AppRuleEntity, isInstalled: Boolean): Boolean {
        if (!isInstalled) {
            return true
        }

        if (
            rule.isPinned ||
            rule.lastSeenAt > 0L ||
            rule.isEnabled ||
            rule.defaultAction != RuleAction.BLOCK.name ||
            rule.mirrorCooldownMillisOverride != null ||
            rule.mirrorBurstStrategyOverride != null
        ) {
            return true
        }

        return dao.hasKeywordRule(rule.packageName) ||
            dao.hasChannelRules(rule.packageName) ||
            dao.hasAppSchedules(rule.packageName)
    }

    private suspend fun buildComprehensiveAppListItems(
        rules: List<AppRuleEntity>,
    ): List<AppListItem> {
        val installedApps = queryInstalledApps()
        val installedPackages = installedApps.mapTo(mutableSetOf()) { it.packageName }
        val rulesByPackage = rules.associateBy { it.packageName }

        val installedItems = installedApps.map { installedApp ->
            val existingRule = rulesByPackage[installedApp.packageName]
            AppListItem(
                packageName = installedApp.packageName,
                appName = installedApp.appName,
                lastSeenAt = existingRule?.lastSeenAt ?: 0L,
                isEnabled = existingRule?.isEnabled ?: false,
                isInstalled = true,
                isSystemApp = installedApp.isSystemApp,
                keepVisibleInUserApps = existingRule?.let {
                    shouldKeepVisibleInUserApps(it, isInstalled = true)
                } ?: false,
            )
        }

        val extraRuleItems = rules.mapNotNull { rule ->
            if (installedPackages.contains(rule.packageName) || !shouldShowInAppList(rule, isInstalled = false)) {
                return@mapNotNull null
            }

            AppListItem(
                packageName = rule.packageName,
                appName = rule.appName.ifBlank { rule.packageName },
                lastSeenAt = rule.lastSeenAt,
                isEnabled = rule.isEnabled,
                isInstalled = false,
                isSystemApp = false,
                keepVisibleInUserApps = true,
            )
        }

        return (installedItems + extraRuleItems).sortedWith(
            compareBy<AppListItem> { it.appName.lowercase(Locale.getDefault()) }
                .thenBy { it.packageName.lowercase(Locale.getDefault()) },
        )
    }

    private fun queryInstalledApps(): List<DiscoveredApp> {
        val applicationInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

        return applicationInfos
            .asSequence()
            .mapNotNull { appInfo ->
                val packageName = appInfo.packageName?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                DiscoveredApp(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(appInfo)
                        ?.toString()
                        ?.ifBlank { packageName }
                        ?: packageName,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
            .distinctBy { it.packageName }
            .toList()
    }

    private fun resolveAppLabel(packageName: String, fallback: String): String {
        return runCatching {
            val appInfo = getApplicationInfo(packageName)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(fallback.ifBlank { packageName })
    }

    private fun getApplicationInfo(packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
    }

    private data class DiscoveredApp(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
    )

}
