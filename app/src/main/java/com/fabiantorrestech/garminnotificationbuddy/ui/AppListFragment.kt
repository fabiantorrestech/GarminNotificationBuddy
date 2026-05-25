package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.MainActivity
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.data.AppListItem
import com.fabiantorrestech.garminnotificationbuddy.data.ManualPackageAddResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppListFragment : Fragment(R.layout.fragment_app_list) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }
    private val searchQuery = MutableStateFlow("")
    private val filterMode = MutableStateFlow(AppFilterMode.USER_APPS)
    private val hasCompletedInitialRefresh = MutableStateFlow(false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emptyView = view.findViewById<TextView>(R.id.emptyAppsTextView)
        val userAppsButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.userAppsFilterButton)
        val allInstalledButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.allInstalledFilterButton)
        val adapter = AppRuleAdapter(
            bindPrimaryButton = { button, item ->
                bindEnabledStateButton(button, item.isEnabled)
            },
            onPrimaryClicked = { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.setAppEnabled(item.packageName, !item.isEnabled)
                }
            },
            secondaryButtonText = getString(R.string.details_label),
            onSecondaryClicked = { item ->
                (requireActivity() as MainActivity).openAppDetail(item.packageName)
            },
        )

        view.findViewById<RecyclerView>(R.id.appRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        installMenu()
        bindFilterButtons(
            selectedMode = filterMode.value,
            userAppsButton = userAppsButton,
            allInstalledButton = allInstalledButton,
        )
        userAppsButton.setOnClickListener {
            filterMode.value = AppFilterMode.USER_APPS
            bindFilterButtons(AppFilterMode.USER_APPS, userAppsButton, allInstalledButton)
        }
        allInstalledButton.setOnClickListener {
            filterMode.value = AppFilterMode.ALL_INSTALLED
            bindFilterButtons(AppFilterMode.ALL_INSTALLED, userAppsButton, allInstalledButton)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                container.ruleRepository.refreshAppList()
            } finally {
                hasCompletedInitialRefresh.value = true
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    container.ruleRepository.observeAppListItems(),
                    searchQuery,
                    filterMode,
                    hasCompletedInitialRefresh,
                ) { items, query, selectedFilter, hasRefreshed ->
                    AppListUiState(
                        items = filterItems(items, query, selectedFilter),
                        query = query,
                        hasRefreshed = hasRefreshed,
                    )
                }.collect { state ->
                    adapter.submitList(state.items)
                    emptyView.text = getString(
                        if (state.query.isBlank()) {
                            R.string.app_list_empty_generic
                        } else {
                            R.string.app_list_empty_search
                        },
                    )
                    emptyView.isVisible = state.hasRefreshed && state.items.isEmpty()
                }
            }
        }
    }

    private fun installMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.app_list_menu, menu)

                    val searchItem = menu.findItem(R.id.action_search)
                    val searchView = searchItem.actionView as SearchView
                    searchView.queryHint = getString(R.string.app_list_search_hint)
                    searchView.setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String?): Boolean {
                                searchQuery.value = query.orEmpty()
                                return true
                            }

                            override fun onQueryTextChange(newText: String?): Boolean {
                                searchQuery.value = newText.orEmpty()
                                return true
                            }
                        },
                    )
                    searchItem.setOnActionExpandListener(
                        object : MenuItem.OnActionExpandListener {
                            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

                            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                                searchQuery.value = ""
                                return true
                            }
                        },
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_refresh -> {
                            refreshAppList()
                            true
                        }
                        R.id.action_add_app -> {
                            showManualPackageDialog()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun refreshAppList() {
        viewLifecycleOwner.lifecycleScope.launch {
            hasCompletedInitialRefresh.value = false
            try {
                container.ruleRepository.refreshAppList()
            } finally {
                hasCompletedInitialRefresh.value = true
            }
        }
    }

    private fun showManualPackageDialog() {
        val context = requireContext()
        val horizontalPadding = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(context).apply {
            hint = context.getString(R.string.manual_package_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val content = FrameLayout(context).apply {
            setPadding(horizontalPadding, 8, horizontalPadding, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.manual_package_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add_label, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    when (container.ruleRepository.addManualPackage(input.text?.toString().orEmpty())) {
                        ManualPackageAddResult.ADDED -> {
                            dialog.dismiss()
                        }
                        ManualPackageAddResult.ALREADY_EXISTS -> {
                            Toast.makeText(
                                context,
                                R.string.manual_package_exists,
                                Toast.LENGTH_SHORT,
                            ).show()
                            dialog.dismiss()
                        }
                        ManualPackageAddResult.INVALID -> {
                            input.error = context.getString(R.string.manual_package_invalid)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun filterItems(
        items: List<AppListItem>,
        query: String,
        selectedFilter: AppFilterMode,
    ): List<AppListItem> {
        val normalizedQuery = query.trim()
        return items.filter { item ->
            matchesFilterMode(item, selectedFilter) &&
                (
                    normalizedQuery.isBlank() ||
                        item.appName.contains(normalizedQuery, ignoreCase = true) ||
                        item.packageName.contains(normalizedQuery, ignoreCase = true)
                    )
        }
    }

    private fun matchesFilterMode(item: AppListItem, selectedFilter: AppFilterMode): Boolean {
        return when (selectedFilter) {
            AppFilterMode.USER_APPS -> !item.isSystemApp || item.keepVisibleInUserApps
            AppFilterMode.ALL_INSTALLED -> item.isInstalled || item.keepVisibleInUserApps
        }
    }

    private fun bindFilterButtons(
        selectedMode: AppFilterMode,
        userAppsButton: com.google.android.material.button.MaterialButton,
        allInstalledButton: com.google.android.material.button.MaterialButton,
    ) {
        userAppsButton.isChecked = selectedMode == AppFilterMode.USER_APPS
        allInstalledButton.isChecked = selectedMode == AppFilterMode.ALL_INSTALLED
    }

    private data class AppListUiState(
        val items: List<AppListItem>,
        val query: String,
        val hasRefreshed: Boolean,
    )

    private enum class AppFilterMode {
        USER_APPS,
        ALL_INSTALLED,
    }
}
