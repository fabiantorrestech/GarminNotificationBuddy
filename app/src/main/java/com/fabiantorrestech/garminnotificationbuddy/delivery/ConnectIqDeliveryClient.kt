package com.fabiantorrestech.garminnotificationbuddy.delivery

import android.content.Context
import android.content.pm.PackageManager
import com.fabiantorrestech.garminnotificationbuddy.model.DeliveryResult
import com.fabiantorrestech.garminnotificationbuddy.model.GarminSetupStatus
import com.fabiantorrestech.garminnotificationbuddy.model.NotificationEvent
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ConnectIqDeliveryClient(
    private val context: Context,
) : WatchDeliveryClient {
    private val connectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
    private val statusFlow = MutableStateFlow(GarminSetupStatus())
    private val iqApp = IQApp(WATCH_APP_ID)

    private var sdkReady = false
    private var activeDevice: IQDevice? = null

    val setupStatus: StateFlow<GarminSetupStatus> = statusFlow

    fun initialize() {
        connectIQ.initialize(context, true, object : ConnectIQ.ConnectIQListener {
            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                sdkReady = false
                statusFlow.value = statusFlow.value.copy(
                    sdkReady = false,
                    lastError = errStatus.name.lowercase(),
                )
                refreshSetupStatus()
            }

            override fun onSdkReady() {
                sdkReady = true
                refreshSetupStatus()
            }

            override fun onSdkShutDown() {
                sdkReady = false
                activeDevice = null
                statusFlow.value = statusFlow.value.copy(
                    sdkReady = false,
                    deviceName = null,
                    deviceStatus = null,
                    watchAppInstalled = false,
                    lastError = "sdk_shutdown",
                )
            }
        })
    }

    fun refreshSetupStatus() {
        val garminConnectInstalled = isGarminConnectInstalled()
        if (!garminConnectInstalled) {
            activeDevice = null
            statusFlow.value = GarminSetupStatus(
                garminConnectInstalled = false,
                sdkReady = sdkReady,
                lastError = "garmin_connect_missing",
            )
            return
        }

        if (!sdkReady) {
            statusFlow.value = GarminSetupStatus(
                garminConnectInstalled = true,
                sdkReady = false,
                lastError = statusFlow.value.lastError ?: "sdk_not_ready",
            )
            return
        }

        try {
            val devices = connectIQ.knownDevices.orEmpty()
            val selectedDevice = devices.firstOrNull {
                connectIQ.getDeviceStatus(it) == IQDevice.IQDeviceStatus.CONNECTED
            } ?: devices.firstOrNull()

            if (selectedDevice == null) {
                activeDevice = null
                statusFlow.value = GarminSetupStatus(
                    garminConnectInstalled = true,
                    sdkReady = true,
                    lastError = "no_known_devices",
                )
                return
            }

            val currentStatus = connectIQ.getDeviceStatus(selectedDevice)
            selectedDevice.status = currentStatus
            activeDevice = selectedDevice
            queryAppInstallStatus(selectedDevice)
        } catch (exception: ServiceUnavailableException) {
            activeDevice = null
            statusFlow.value = GarminSetupStatus(
                garminConnectInstalled = true,
                sdkReady = false,
                lastError = "service_unavailable:${exception.javaClass.simpleName}",
            )
        } catch (_: InvalidStateException) {
            activeDevice = null
            statusFlow.value = GarminSetupStatus(
                garminConnectInstalled = true,
                sdkReady = false,
                lastError = "invalid_sdk_state",
            )
        }
    }

    fun openStore() {
        if (sdkReady) {
            connectIQ.openStore(WATCH_APP_ID)
        }
    }

    override suspend fun deliver(event: NotificationEvent): DeliveryResult = withContext(Dispatchers.IO) {
        refreshSetupStatus()
        val device = activeDevice
        val currentSetup = statusFlow.value

        if (!currentSetup.garminConnectInstalled) {
            return@withContext DeliveryResult(false, "garmin_connect_missing")
        }
        if (!currentSetup.sdkReady || device == null) {
            return@withContext DeliveryResult(false, currentSetup.lastError ?: "connect_iq_not_ready")
        }
        if (!currentSetup.watchAppInstalled) {
            return@withContext DeliveryResult(false, "watch_app_not_installed")
        }

        val payload = hashMapOf(
            "id" to event.id,
            "sourceApp" to event.appName,
            "channelId" to event.channelId,
            "title" to event.title,
            "body" to event.expandedText.ifBlank { event.text },
            "timestamp" to event.postedAt.toString(),
        )

        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                connectIQ.sendMessage(device, iqApp, payload) { _, _, status ->
                    val wasSuccessful = status.name == "SUCCESS"
                    continuation.resume(
                        DeliveryResult(
                            success = wasSuccessful,
                            reason = if (wasSuccessful) "delivered_connect_iq" else status.name.lowercase(),
                        ),
                    )
                }
            } catch (_: InvalidStateException) {
                continuation.resume(DeliveryResult(false, "invalid_sdk_state"))
            } catch (_: ServiceUnavailableException) {
                continuation.resume(DeliveryResult(false, "service_unavailable"))
            }
        }
    }

    private fun queryAppInstallStatus(device: IQDevice) {
        try {
            connectIQ.getApplicationInfo(WATCH_APP_ID, device, object : ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    statusFlow.value = GarminSetupStatus(
                        garminConnectInstalled = true,
                        sdkReady = true,
                        deviceName = device.friendlyName,
                        deviceStatus = device.status?.name,
                        watchAppInstalled = app.status == IQApp.IQAppStatus.INSTALLED,
                    )
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    statusFlow.value = GarminSetupStatus(
                        garminConnectInstalled = true,
                        sdkReady = true,
                        deviceName = device.friendlyName,
                        deviceStatus = device.status?.name,
                        watchAppInstalled = false,
                        lastError = "watch_app_not_installed",
                    )
                }
            })
        } catch (_: InvalidStateException) {
            statusFlow.value = GarminSetupStatus(
                garminConnectInstalled = true,
                sdkReady = false,
                lastError = "invalid_sdk_state",
            )
        } catch (_: ServiceUnavailableException) {
            statusFlow.value = GarminSetupStatus(
                garminConnectInstalled = true,
                sdkReady = false,
                lastError = "service_unavailable",
            )
        }
    }

    private fun isGarminConnectInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(GARMIN_CONNECT_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
        const val WATCH_APP_ID = "9d736064-2f24-4a51-9c8a-24db4a9ed9d6"
    }
}
