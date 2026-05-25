package com.fabiantorrestech.garminnotificationbuddy

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.fabiantorrestech.garminnotificationbuddy.ui.AppDetailFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.AppListFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.DeliveryLogFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.HomeFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.OnboardingFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.ScheduleDetailFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.SchedulesFragment
import com.fabiantorrestech.garminnotificationbuddy.ui.SettingsFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigationView: BottomNavigationView
    private var currentRootItemId: Int = R.id.navigation_home

    private val postNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        setSupportActionBar(toolbar)
        applyBottomNavigationColors()
        applyEdgeToEdgeInsets()

        currentRootItemId = savedInstanceState?.getInt(KEY_CURRENT_ROOT_ITEM_ID, R.id.navigation_home)
            ?: R.id.navigation_home

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportFragmentManager.addOnBackStackChangedListener(
            FragmentManager.OnBackStackChangedListener { updateChrome() },
        )

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> showRootFragment(
                    fragment = HomeFragment(),
                    menuItemId = item.itemId,
                    title = getString(R.string.nav_home),
                )
                R.id.navigation_apps -> showRootFragment(
                    fragment = AppListFragment(),
                    menuItemId = item.itemId,
                    title = getString(R.string.nav_apps),
                )
                R.id.navigation_schedules -> showRootFragment(
                    fragment = SchedulesFragment(),
                    menuItemId = item.itemId,
                    title = getString(R.string.nav_schedules),
                )
                R.id.navigation_logs -> showRootFragment(
                    fragment = DeliveryLogFragment(),
                    menuItemId = item.itemId,
                    title = getString(R.string.nav_logs),
                )
                R.id.navigation_settings -> showRootFragment(
                    fragment = SettingsFragment(),
                    menuItemId = item.itemId,
                    title = getString(R.string.nav_settings),
                )
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_home
        } else {
            updateChrome()
        }
    }

    fun openAppDetail(packageName: String) {
        currentRootItemId = R.id.navigation_apps
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AppDetailFragment.newInstance(packageName))
            .addToBackStack(packageName)
            .commit()
        updateChrome()
    }

    fun openOnboarding() {
        currentRootItemId = R.id.navigation_settings
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, OnboardingFragment())
            .addToBackStack(ONBOARDING_BACK_STACK_NAME)
            .commit()
        updateChrome()
    }

    fun openScheduleDetail(
        scheduleId: Long? = null,
        preselectedPackageName: String? = null,
    ) {
        currentRootItemId = if (preselectedPackageName == null) {
            R.id.navigation_schedules
        } else {
            R.id.navigation_apps
        }
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                ScheduleDetailFragment.newInstance(
                    scheduleId = scheduleId,
                    preselectedPackageName = preselectedPackageName,
                ),
            )
            .addToBackStack(scheduleId?.toString() ?: SCHEDULE_DETAIL_BACK_STACK_NAME)
            .commit()
        updateChrome()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_ROOT_ITEM_ID, currentRootItemId)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    fun updateScheduleDetailTitle(title: String) {
        supportActionBar?.title = title
    }

    private fun showRootFragment(fragment: Fragment, menuItemId: Int, title: String): Boolean {
        currentRootItemId = menuItemId
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = title
        updateChrome()
        return true
    }

    private fun updateChrome() {
        val isNestedScreen = supportFragmentManager.backStackEntryCount > 0
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        supportActionBar?.setDisplayHomeAsUpEnabled(isNestedScreen)
        supportActionBar?.title = when (currentFragment) {
            is AppDetailFragment -> getString(R.string.app_detail_title)
            is ScheduleDetailFragment -> currentFragment.currentToolbarTitle(this)
            is OnboardingFragment -> getString(R.string.onboarding_title)
            else -> titleForRootItem(currentRootItemId)
        }
        toolbar.logo = if (!isNestedScreen && currentRootItemId == R.id.navigation_home && currentFragment is HomeFragment) {
            AppCompatResources.getDrawable(this, R.drawable.ic_launcher_monochrome)
        } else {
            null
        }
    }

    private fun titleForRootItem(itemId: Int): String {
        return when (itemId) {
            R.id.navigation_home -> getString(R.string.nav_home)
            R.id.navigation_apps -> getString(R.string.nav_apps)
            R.id.navigation_schedules -> getString(R.string.nav_schedules)
            R.id.navigation_logs -> getString(R.string.nav_logs)
            R.id.navigation_settings -> getString(R.string.nav_settings)
            else -> getString(R.string.app_name)
        }
    }

    private fun applyEdgeToEdgeInsets() {
        val rootView = findViewById<android.view.View>(R.id.rootLayout)
        val fragmentContainer = findViewById<android.view.View>(R.id.fragmentContainer)
        val toolbarStart = toolbar.paddingStart
        val toolbarTop = toolbar.paddingTop
        val toolbarEnd = toolbar.paddingEnd
        val toolbarBottom = toolbar.paddingBottom
        val bottomStart = bottomNavigationView.paddingStart
        val bottomTop = bottomNavigationView.paddingTop
        val bottomEnd = bottomNavigationView.paddingEnd
        val bottomBottom = bottomNavigationView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )

            toolbar.updatePadding(
                left = toolbarStart + systemBars.left,
                top = toolbarTop + systemBars.top,
                right = toolbarEnd + systemBars.right,
                bottom = toolbarBottom,
            )
            fragmentContainer.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
            )
            bottomNavigationView.updatePadding(
                left = bottomStart + systemBars.left,
                top = bottomTop,
                right = bottomEnd + systemBars.right,
                bottom = bottomBottom + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun applyBottomNavigationColors() {
        val checkedColor = resolveThemeColor(android.R.attr.colorAccent)
        val uncheckedColor = resolveThemeColor(android.R.attr.textColorSecondary)
        val itemColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checkedColor, uncheckedColor),
        )
        bottomNavigationView.itemIconTintList = itemColors
        bottomNavigationView.itemTextColor = itemColors
        bottomNavigationView.itemActiveIndicatorColor = ColorStateList.valueOf(
            resolveThemeColor(android.R.attr.colorBackgroundFloating),
        )
    }

    private fun resolveThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    companion object {
        private const val KEY_CURRENT_ROOT_ITEM_ID = "currentRootItemId"
        private const val ONBOARDING_BACK_STACK_NAME = "onboarding"
        private const val SCHEDULE_DETAIL_BACK_STACK_NAME = "schedule_detail"
    }
}
