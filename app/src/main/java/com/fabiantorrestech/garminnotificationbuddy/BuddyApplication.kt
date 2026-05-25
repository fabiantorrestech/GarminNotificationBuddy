package com.fabiantorrestech.garminnotificationbuddy

import android.app.Application
import com.fabiantorrestech.garminnotificationbuddy.data.BuddyDatabase
import com.fabiantorrestech.garminnotificationbuddy.data.DeliveryLogRepository
import com.fabiantorrestech.garminnotificationbuddy.data.HomeStatusRepository
import com.fabiantorrestech.garminnotificationbuddy.data.RuleRepository
import com.fabiantorrestech.garminnotificationbuddy.delivery.GarminConnectStatusChecker
import com.fabiantorrestech.garminnotificationbuddy.delivery.ProxyMirrorBurstCoordinator
import com.fabiantorrestech.garminnotificationbuddy.delivery.ProxyMirrorDeliveryClient
import com.fabiantorrestech.garminnotificationbuddy.domain.DecisionEngine
import com.fabiantorrestech.garminnotificationbuddy.domain.NotificationNormalizer
import com.fabiantorrestech.garminnotificationbuddy.domain.PhoneDndStateProvider
import com.fabiantorrestech.garminnotificationbuddy.domain.ScheduleEvaluator
import com.fabiantorrestech.garminnotificationbuddy.processing.NotificationProcessor
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BuddyApplication : Application() {
    lateinit var container: BuddyContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
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
    val garminConnectStatusChecker = GarminConnectStatusChecker(application)
    private val scheduleEvaluator = ScheduleEvaluator()
    val homeStatusRepository = HomeStatusRepository(
        context = application,
        ruleRepository = ruleRepository,
        deliveryLogRepository = deliveryLogRepository,
        garminConnectStatusChecker = garminConnectStatusChecker,
        phoneDndStateProvider = phoneDndStateProvider,
        scheduleEvaluator = scheduleEvaluator,
    )
    val proxyMirrorDeliveryClient = ProxyMirrorDeliveryClient(application)
    val proxyMirrorBurstCoordinator = ProxyMirrorBurstCoordinator(
        applicationScope = applicationScope,
        ruleRepository = ruleRepository,
        deliveryLogRepository = deliveryLogRepository,
        proxyMirrorDeliveryClient = proxyMirrorDeliveryClient,
    )
    val decisionEngine = DecisionEngine(
        ruleRepository = ruleRepository,
        scheduleEvaluator = scheduleEvaluator,
        phoneDndStateProvider = phoneDndStateProvider,
    )
    val notificationProcessor = NotificationProcessor(
        ruleRepository = ruleRepository,
        deliveryLogRepository = deliveryLogRepository,
        decisionEngine = decisionEngine,
        normalizer = NotificationNormalizer(application.packageManager, application.packageName),
        proxyMirrorBurstCoordinator = proxyMirrorBurstCoordinator,
    )

    init {
        applicationScope.launch {
            ruleRepository.ensureDefaults()
        }
        proxyMirrorDeliveryClient.ensureSilentMirrorChannel()
    }
}
