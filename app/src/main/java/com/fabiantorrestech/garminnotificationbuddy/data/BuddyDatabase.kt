package com.fabiantorrestech.garminnotificationbuddy.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class GlobalSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val masterEnabled: Boolean = true,
    val deliveryMode: String = "PROXY_MIRROR",
    val followPhoneDndRules: Boolean = true,
    val mirrorCooldownMillis: Int = 5_000,
    val mirrorBurstStrategy: String = "LATEST_ONLY",
)

@Entity
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = false,
    val defaultAction: String = "BLOCK",
    val lastSeenAt: Long = 0L,
    val isPinned: Boolean = false,
    val mirrorCooldownMillisOverride: Int? = null,
    val mirrorBurstStrategyOverride: String? = null,
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
    val name: String,
    val daysMask: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val isEnabled: Boolean = true,
)

@Entity(
    primaryKeys = ["scheduleId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("scheduleId"),
        Index("packageName"),
    ],
)
data class ScheduleAppAssignmentEntity(
    val scheduleId: Long,
    val packageName: String,
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

data class ScheduleWithAssignmentCount(
    @Embedded val schedule: ScheduleEntity,
    @ColumnInfo(name = "assignedAppCount") val assignedAppCount: Int,
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

    @Query("SELECT * FROM AppRuleEntity")
    suspend fun getAllAppRules(): List<AppRuleEntity>

    @Query("SELECT * FROM AppRuleEntity WHERE packageName = :packageName")
    fun observeAppRule(packageName: String): Flow<AppRuleEntity?>

    @Query("SELECT * FROM AppRuleEntity WHERE packageName = :packageName")
    suspend fun getAppRule(packageName: String): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppRule(rule: AppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppRules(rules: List<AppRuleEntity>)

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

    @Query("SELECT EXISTS(SELECT 1 FROM KeywordRuleEntity WHERE packageName = :packageName)")
    suspend fun hasKeywordRule(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKeywordRule(rule: KeywordRuleEntity)

    @Query(
        "SELECT ScheduleEntity.*, COUNT(ScheduleAppAssignmentEntity.packageName) AS assignedAppCount " +
            "FROM ScheduleEntity " +
            "LEFT JOIN ScheduleAppAssignmentEntity " +
            "ON ScheduleEntity.id = ScheduleAppAssignmentEntity.scheduleId " +
            "GROUP BY ScheduleEntity.id " +
            "ORDER BY ScheduleEntity.name COLLATE NOCASE"
    )
    fun observeScheduleListItems(): Flow<List<ScheduleWithAssignmentCount>>

    @Query(
        "SELECT ScheduleEntity.*, COUNT(ScheduleAppAssignmentEntity.packageName) AS assignedAppCount " +
            "FROM ScheduleEntity " +
            "INNER JOIN ScheduleAppAssignmentEntity " +
            "ON ScheduleEntity.id = ScheduleAppAssignmentEntity.scheduleId " +
            "WHERE ScheduleAppAssignmentEntity.packageName = :packageName " +
            "GROUP BY ScheduleEntity.id " +
            "ORDER BY ScheduleEntity.name COLLATE NOCASE"
    )
    fun observeSchedulesForApp(packageName: String): Flow<List<ScheduleWithAssignmentCount>>

    @Query(
        "SELECT * FROM ScheduleEntity " +
            "WHERE id NOT IN (SELECT scheduleId FROM ScheduleAppAssignmentEntity) " +
            "ORDER BY name COLLATE NOCASE"
    )
    suspend fun getGlobalSchedules(): List<ScheduleEntity>

    @Query(
        "SELECT ScheduleEntity.* FROM ScheduleEntity " +
            "INNER JOIN ScheduleAppAssignmentEntity " +
            "ON ScheduleEntity.id = ScheduleAppAssignmentEntity.scheduleId " +
            "WHERE ScheduleAppAssignmentEntity.packageName = :packageName"
    )
    suspend fun getSchedulesForApp(packageName: String): List<ScheduleEntity>

    @Query("SELECT * FROM ScheduleEntity WHERE id = :scheduleId")
    fun observeSchedule(scheduleId: Long): Flow<ScheduleEntity?>

    @Query("SELECT * FROM ScheduleEntity WHERE id = :scheduleId")
    suspend fun getSchedule(scheduleId: Long): ScheduleEntity?

    @Query("SELECT packageName FROM ScheduleAppAssignmentEntity WHERE scheduleId = :scheduleId")
    fun observeScheduleAssignments(scheduleId: Long): Flow<List<String>>

    @Query("SELECT packageName FROM ScheduleAppAssignmentEntity WHERE scheduleId = :scheduleId")
    suspend fun getScheduleAssignments(scheduleId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleAssignments(assignments: List<ScheduleAppAssignmentEntity>)

    @Query("DELETE FROM ScheduleAppAssignmentEntity WHERE scheduleId = :scheduleId")
    suspend fun deleteScheduleAssignments(scheduleId: Long)

    @Query("DELETE FROM ScheduleEntity WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM ChannelRuleEntity WHERE packageName = :packageName)")
    suspend fun hasChannelRules(packageName: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM ScheduleAppAssignmentEntity WHERE packageName = :packageName)")
    suspend fun hasAppSchedules(packageName: String): Boolean

    @Query("SELECT * FROM DeliveryLogEntity ORDER BY timestamp DESC LIMIT 200")
    fun observeDeliveryLogs(): Flow<List<DeliveryLogEntity>>

    @Query("SELECT MAX(timestamp) FROM DeliveryLogEntity WHERE decision = 'DELIVERED'")
    fun observeLatestDeliveredTimestamp(): Flow<Long?>

    @Query(
        "SELECT COUNT(*) FROM DeliveryLogEntity " +
            "WHERE decision = 'DELIVERED' AND timestamp >= :cutoffTimestamp"
    )
    suspend fun countDeliveredSince(cutoffTimestamp: Long): Int

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
        ScheduleAppAssignmentEntity::class,
        DeliveryLogEntity::class,
    ],
    version = 4,
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
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE GlobalSettingsEntity " +
                        "ADD COLUMN mirrorCooldownSeconds INTEGER NOT NULL DEFAULT 5",
                )
                db.execSQL(
                    "ALTER TABLE GlobalSettingsEntity " +
                        "ADD COLUMN mirrorBurstStrategy TEXT NOT NULL DEFAULT 'LATEST_ONLY'",
                )
                db.execSQL(
                    "ALTER TABLE AppRuleEntity " +
                        "ADD COLUMN mirrorCooldownSecondsOverride INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE AppRuleEntity " +
                        "ADD COLUMN mirrorBurstStrategyOverride TEXT",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE AppRuleEntity " +
                        "ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS GlobalSettingsEntity_new (" +
                        "id INTEGER NOT NULL, " +
                        "masterEnabled INTEGER NOT NULL, " +
                        "deliveryMode TEXT NOT NULL, " +
                        "followPhoneDndRules INTEGER NOT NULL, " +
                        "mirrorCooldownMillis INTEGER NOT NULL, " +
                        "mirrorBurstStrategy TEXT NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                db.execSQL(
                    "INSERT INTO GlobalSettingsEntity_new (" +
                        "id, masterEnabled, deliveryMode, followPhoneDndRules, mirrorCooldownMillis, mirrorBurstStrategy" +
                        ") " +
                        "SELECT id, masterEnabled, deliveryMode, " +
                        "CASE WHEN syncWithPhoneDnd = 1 THEN 0 ELSE 1 END, " +
                        "mirrorCooldownSeconds * 1000, mirrorBurstStrategy " +
                        "FROM GlobalSettingsEntity",
                )
                db.execSQL("DROP TABLE GlobalSettingsEntity")
                db.execSQL("ALTER TABLE GlobalSettingsEntity_new RENAME TO GlobalSettingsEntity")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS AppRuleEntity_new (" +
                        "packageName TEXT NOT NULL, " +
                        "appName TEXT NOT NULL, " +
                        "isEnabled INTEGER NOT NULL, " +
                        "defaultAction TEXT NOT NULL, " +
                        "lastSeenAt INTEGER NOT NULL, " +
                        "isPinned INTEGER NOT NULL, " +
                        "mirrorCooldownMillisOverride INTEGER, " +
                        "mirrorBurstStrategyOverride TEXT, " +
                        "PRIMARY KEY(packageName))",
                )
                db.execSQL(
                    "INSERT INTO AppRuleEntity_new (" +
                        "packageName, appName, isEnabled, defaultAction, lastSeenAt, isPinned, " +
                        "mirrorCooldownMillisOverride, mirrorBurstStrategyOverride" +
                        ") " +
                        "SELECT packageName, appName, isEnabled, defaultAction, lastSeenAt, isPinned, " +
                        "CASE " +
                        "WHEN mirrorCooldownSecondsOverride IS NULL THEN NULL " +
                        "ELSE mirrorCooldownSecondsOverride * 1000 " +
                        "END, " +
                        "mirrorBurstStrategyOverride " +
                        "FROM AppRuleEntity",
                )
                db.execSQL("DROP TABLE AppRuleEntity")
                db.execSQL("ALTER TABLE AppRuleEntity_new RENAME TO AppRuleEntity")

                db.execSQL("ALTER TABLE ScheduleEntity RENAME TO ScheduleEntity_legacy")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ScheduleEntity (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "daysMask INTEGER NOT NULL, " +
                        "startMinuteOfDay INTEGER NOT NULL, " +
                        "endMinuteOfDay INTEGER NOT NULL, " +
                        "isEnabled INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO ScheduleEntity (id, name, daysMask, startMinuteOfDay, endMinuteOfDay, isEnabled) " +
                        "SELECT id, name, daysMask, startMinuteOfDay, endMinuteOfDay, isEnabled " +
                        "FROM ScheduleEntity_legacy",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ScheduleAppAssignmentEntity (" +
                        "scheduleId INTEGER NOT NULL, " +
                        "packageName TEXT NOT NULL, " +
                        "PRIMARY KEY(scheduleId, packageName), " +
                        "FOREIGN KEY(scheduleId) REFERENCES ScheduleEntity(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ScheduleAppAssignmentEntity_scheduleId " +
                        "ON ScheduleAppAssignmentEntity(scheduleId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ScheduleAppAssignmentEntity_packageName " +
                        "ON ScheduleAppAssignmentEntity(packageName)",
                )
                db.execSQL(
                    "INSERT INTO ScheduleAppAssignmentEntity (scheduleId, packageName) " +
                        "SELECT id, scopeKey FROM ScheduleEntity_legacy " +
                        "WHERE scopeType = 'APP' AND scopeKey IS NOT NULL",
                )
                db.execSQL("DROP TABLE ScheduleEntity_legacy")
            }
        }
    }
}
