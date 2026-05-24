package com.fabiantorrestech.garminnotificationbuddy.delivery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class GarminConnectStatusChecker(
    private val context: Context,
) {
    fun isGarminConnectInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    GARMIN_CONNECT_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(GARMIN_CONNECT_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
    }
}
