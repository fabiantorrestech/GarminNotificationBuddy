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
import com.fabiantorrestech.garminnotificationbuddy.R
import kotlinx.coroutines.launch

class DeliveryLogFragment : Fragment(R.layout.fragment_delivery_log) {
    private val container by lazy { (requireActivity().application as BuddyApplication).container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emptyView = view.findViewById<TextView>(R.id.emptyLogsTextView)
        val adapter = DeliveryLogAdapter()

        view.findViewById<RecyclerView>(R.id.deliveryLogRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.deliveryLogRepository.observeLogs().collect { logs ->
                    adapter.submitList(logs)
                    emptyView.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
