package com.fabiantorrestech.garminnotificationbuddy.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.garminnotificationbuddy.BuddyApplication
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.model.ScheduleScope
import kotlinx.coroutines.launch

class SchedulesFragment : Fragment(R.layout.fragment_schedules) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emptyView = view.findViewById<TextView>(R.id.emptyGlobalSchedulesTextView)
        val adapter = ScheduleAdapter(
            onEditClicked = { schedule ->
                ScheduleEditorDialog.show(
                    fragment = this,
                    existing = schedule,
                    scope = ScheduleScope.GLOBAL,
                    scopeKey = null,
                ) { updatedSchedule ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        container.ruleRepository.saveSchedule(updatedSchedule)
                    }
                }
            },
            onDeleteClicked = { schedule ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.deleteSchedule(schedule.id)
                }
            },
        )

        view.findViewById<RecyclerView>(R.id.globalScheduleRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        view.findViewById<Button>(R.id.addGlobalScheduleButton).setOnClickListener {
            ScheduleEditorDialog.show(
                fragment = this,
                existing = null,
                scope = ScheduleScope.GLOBAL,
                scopeKey = null,
            ) { schedule ->
                viewLifecycleOwner.lifecycleScope.launch {
                    container.ruleRepository.saveSchedule(schedule)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.ruleRepository.observeSchedules(ScheduleScope.GLOBAL, null).collect { schedules ->
                    adapter.submitList(schedules)
                    emptyView.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
