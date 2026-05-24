package com.fabiantorrestech.garminnotificationbuddy

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.fabiantorrestech.garminnotificationbuddy.ui.AppDetailFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.AppListFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.DeliveryLogFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.OnboardingFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.SchedulesFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.SettingsFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        setSupportActionBar(toolbar)

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_onboarding -> showRootFragment(OnboardingFragment(), getString(R.string.nav_onboarding))
                R.id.navigation_apps -> showRootFragment(AppListFragment(), getString(R.string.nav_apps))
                R.id.navigation_schedules -> showRootFragment(SchedulesFragment(), getString(R.string.nav_schedules))
                R.id.navigation_logs -> showRootFragment(DeliveryLogFragment(), getString(R.string.nav_logs))
                R.id.navigation_settings -> showRootFragment(SettingsFragment(), getString(R.string.nav_settings))
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_onboarding
        }
    }

    fun openAppDetail(packageName: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AppDetailFragment.newInstance(packageName))
            .addToBackStack(packageName)
            .commit()
        supportActionBar?.title = getString(R.string.app_detail_title)
        bottomNavigationView.selectedItemId = R.id.navigation_apps
    }

    fun openNotificationListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openNotificationPolicyAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showRootFragment(fragment: androidx.fragment.app.Fragment, title: String): Boolean {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = title
        return true
    }
}
