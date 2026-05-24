package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.MainActivity
import com.fabiantorrestech.garminnotificationbuddy.R
import kotlinx.coroutines.launch

class AppListFragment : Fragment(R.layout.fragment_app_list) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emptyView = view.findViewById<TextView>(R.id.emptyAppsTextView)
        val adapter = AppRuleAdapter(
            onEnabledChanged = { item, isEnabled ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.setAppEnabled(item.packageName, isEnabled)
                }
            },
            onDefaultActionClicked = { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.setAppDefaultAction(item.packageName, nextAction(item.defaultAction))
                }
            },
            onDetailsClicked = { item ->
                (requireActivity() as MainActivity).openAppDetail(item.packageName)
            },
        )

        view.findViewById<RecyclerView>(R.id.appRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.ruleRepository.observeAppRules().collect { appRules ->
                    adapter.submitList(appRules)
                    emptyView.visibility = if (appRules.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
