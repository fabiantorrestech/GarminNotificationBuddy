package com.fabiantorrestech.garminnotificationbuddy.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class GlobalSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val masterEnabled: Boolean = true,
    val deliveryMode: String = "CONNECT_IQ",
    val syncWithPhoneDnd: Boolean = false,
)

@Entity
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = false,
    val defaultAction: String = "BLOCK",
    val lastSeenAt: Long = 0L,
)

@Entity(primaryKeys = ["packageName", "channelId"])
data class ChannelRuleEntity(
    val packageName: String,
    val channelId: String,
    val channelName: String,
    val isEnabled: Boolean = true,
    val overrideAction: String? = null,
    val lastSeenAt: Long = 0L,
)

@Entity
data class KeywordRuleEntity(
    @PrimaryKey val packageName: String,
    val allowlistCsv: String = "",
    val blocklistCsv: String = "",
)

@Entity
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scopeType: String,
    val scopeKey: String? = null,
    val name: String,
    val daysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isEnabled: Boolean = true,
)

@Entity
data class DeliveryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val channelId: String,
    val title: String,
    val body: String,
    val decision: String,
    val reason: String,
    val timestamp: Long,
)

@Dao
interface BuddyDao {
    @Query("SELECT * FROM GlobalSettingsEntity WHERE id = 0")
    fun observeGlobalSettings(): Flow<GlobalSettingsEntity?>

    @Query("SELECT * FROM GlobalSettingsEntity WHERE id = 0")
    suspend fun getGlobalSettings(): GlobalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlobalSettings(settings: GlobalSettingsEntity)

    @Query("SELECT * FROM AppRuleEntity ORDER BY appName COLLATE NOCASE")
    fun observeAppRules(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM AppRuleEntity WHERE packageName = :packageName")
    fun observeAppRule(packageName: String): Flow<AppRuleEntity?>

    @Query("SELECT * FROM AppRuleEntity WHERE packageName = :packageName")
    suspend fun getAppRule(packageName: String): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppRule(rule: AppRuleEntity)

    @Query("SELECT * FROM ChannelRuleEntity WHERE packageName = :packageName ORDER BY channelName COLLATE NOCASE")
    fun observeChannelRules(packageName: String): Flow<List<ChannelRuleEntity>>

    @Query("SELECT * FROM ChannelRuleEntity WHERE packageName = :packageName AND channelId = :channelId")
    suspend fun getChannelRule(packageName: String, channelId: String): ChannelRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannelRule(rule: ChannelRuleEntity)

    @Query("SELECT * FROM KeywordRuleEntity WHERE packageName = :packageName")
    fun observeKeywordRule(packageName: String): Flow<KeywordRuleEntity?>

    @Query("SELECT * FROM KeywordRuleEntity WHERE packageName = :packageName")
    suspend fun getKeywordRule(packageName: String): KeywordRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKeywordRule(rule: KeywordRuleEntity)

    @Query(
        "SELECT * FROM ScheduleEntity WHERE scopeType = :scopeType AND " +
            "((:scopeKey IS NULL AND scopeKey IS NULL) OR scopeKey = :scopeKey) " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun observeSchedules(scopeType: String, scopeKey: String?): Flow<List<ScheduleEntity>>

    @Query(
        "SELECT * FROM ScheduleEntity WHERE scopeType = :scopeType AND " +
            "((:scopeKey IS NULL AND scopeKey IS NULL) OR scopeKey = :scopeKey)"
    )
    suspend fun getSchedules(scopeType: String, scopeKey: String?): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: ScheduleEntity): Long

    @Query("DELETE FROM ScheduleEntity WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: Long)

    @Query("SELECT * FROM DeliveryLogEntity ORDER BY timestamp DESC LIMIT 200")
    fun observeDeliveryLogs(): Flow<List<DeliveryLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveryLog(log: DeliveryLogEntity)
}

@Database(
    entities = [
        GlobalSettingsEntity::class,
        AppRuleEntity::class,
        ChannelRuleEntity::class,
        KeywordRuleEntity::class,
        ScheduleEntity::class,
        DeliveryLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BuddyDatabase : RoomDatabase() {
    abstract fun buddyDao(): BuddyDao

    companion object {
        @Volatile
        private var instance: BuddyDatabase? = null

        fun getInstance(context: Context): BuddyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BuddyDatabase::class.java,
                    "garmin_notification_buddy.db",
                ).build().also { instance = it }
            }
        }
    }
}
