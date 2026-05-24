package com.fabiantorrestech.garminnotificationbuddy

import android.app.Application
import com.fabiantorrestech.garminnotificationbuddy.data.BuddyDatabase
import com.fabiantorrestech.garminnotificationbuddy.data.DeliveryLogRepository
import com.fabiantorrestech.garminnotificationbuddy.data.RuleRepository
import com.fabiantorrestech.garminnotificationbuddy.delivery.ConnectIqDeliveryClient
import com.fabiantorrestech.garminnotificationbuddy.delivery.ProxyMirrorDeliveryClient
import com.fabiantorrestech.garminnotificationbuddy.domain.DecisionEngine
import com.fabiantorrestech.garminnotificationbuddy.domain.NotificationNormalizer
import com.fabiantorrestech.garminnotificationbuddy.domain.PhoneDndStateProvider
import com.fabiantorrestech.garminnotificationbuddy.domain.ScheduleEvaluator
import com.fabiantorrestech.garminnotificationbuddy.processing.NotificationProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BuddyApplication : Application() {
    lateinit var container: BuddyContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = BuddyContainer(this)
    }
}

class BuddyContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = BuddyDatabase.getInstance(application)

    val ruleRepository = RuleRepository(
        dao = database.buddyDao(),
        packageManager = application.packageManager,
    )
    val deliveryLogRepository = DeliveryLogRepository(database.buddyDao())
    val phoneDndStateProvider = PhoneDndStateProvider(application)
    val connectIqDeliveryClient = ConnectIqDeliveryClient(application)
    val proxyMirrorDeliveryClient = ProxyMirrorDeliveryClient(application)
    val decisionEngine = DecisionEngine(
        ruleRepository = ruleRepository,
        scheduleEvaluator = ScheduleEvaluator(),
        phoneDndStateProvider = phoneDndStateProvider,
    )
    val notificationProcessor = NotificationProcessor(
        ruleRepository = ruleRepository,
        deliveryLogRepository = deliveryLogRepository,
        decisionEngine = decisionEngine,
        normalizer = NotificationNormalizer(application.packageManager, application.packageName),
        connectIqDeliveryClient = connectIqDeliveryClient,
        proxyMirrorDeliveryClient = proxyMirrorDeliveryClient,
    )

    init {
        applicationScope.launch {
            ruleRepository.ensureDefaults()
        }
        proxyMirrorDeliveryClient.ensureNotificationChannel()
        connectIqDeliveryClient.initialize()
    }
}
