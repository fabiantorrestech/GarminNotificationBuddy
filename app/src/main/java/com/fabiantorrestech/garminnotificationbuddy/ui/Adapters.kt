package com.fabiantorrestech.garminnotificationbuddy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fabiantorrestech.garminnotificationbuddy.R
import com.fabiantorrestech.garminnotificationbuddy.data.AppRuleEntity
import com.fabiantorrestech.garminnotificationbuddy.data.ChannelRuleEntity
import com.fabiantorrestech.garminnotificationbuddy.data.DeliveryLogEntity
import com.fabiantorrestech.garminnotificationbuddy.data.ScheduleEntity
import com.fabiantorrestech.garminnotificationbuddy.model.RuleAction
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.DateFormat
import java.util.Date

class AppRuleAdapter(
    private val onEnabledChanged: (AppRuleEntity, Boolean) -> Unit,
    private val onDefaultActionClicked: (AppRuleEntity) -> Unit,
    private val onDetailsClicked: (AppRuleEntity) -> Unit,
) : RecyclerView.Adapter<AppRuleAdapter.AppRuleViewHolder>() {
    private val items = mutableListOf<AppRuleEntity>()

    fun submitList(newItems: List<AppRuleEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppRuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_rule, parent, false)
        return AppRuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppRuleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AppRuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.appNameTextView)
        private val packageView: TextView = itemView.findViewById(R.id.packageNameTextView)
        private val lastSeenView: TextView = itemView.findViewById(R.id.lastSeenTextView)
        private val enabledSwitch: SwitchMaterial = itemView.findViewById(R.id.appEnabledSwitch)
        private val defaultActionButton: MaterialButton = itemView.findViewById(R.id.defaultActionButton)
        private val detailsButton: MaterialButton = itemView.findViewById(R.id.detailsButton)

        fun bind(item: AppRuleEntity) {
            nameView.text = item.appName
            packageView.text = item.packageName
            lastSeenView.text = itemView.context.getString(
                R.string.last_seen_template,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.lastSeenAt)),
            )
            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = item.isEnabled
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChanged(item, isChecked)
            }
            defaultActionButton.text = itemView.context.getString(
                R.string.default_action_template,
                item.defaultAction,
            )
            defaultActionButton.setOnClickListener { onDefaultActionClicked(item) }
            detailsButton.setOnClickListener { onDetailsClicked(item) }
        }
    }
}

class ChannelRuleAdapter(
    private val onEnabledChanged: (ChannelRuleEntity, Boolean) -> Unit,
    private val onOverrideClicked: (ChannelRuleEntity) -> Unit,
) : RecyclerView.Adapter<ChannelRuleAdapter.ChannelRuleViewHolder>() {
    private val items = mutableListOf<ChannelRuleEntity>()

    fun submitList(newItems: List<ChannelRuleEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelRuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_rule, parent, false)
        return ChannelRuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelRuleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ChannelRuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.channelNameTextView)
        private val idView: TextView = itemView.findViewById(R.id.channelIdTextView)
        private val enabledSwitch: SwitchMaterial = itemView.findViewById(R.id.channelEnabledSwitch)
        private val overrideButton: MaterialButton = itemView.findViewById(R.id.channelOverrideButton)

        fun bind(item: ChannelRuleEntity) {
            nameView.text = item.channelName
            idView.text = item.channelId
            enabledSwitch.setOnCheckedChangeListener(null)
            enabledSwitch.isChecked = item.isEnabled
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChanged(item, isChecked)
            }
            val overrideLabel = item.overrideAction ?: itemView.context.getString(R.string.inherit_label)
            overrideButton.text = itemView.context.getString(R.string.channel_override_template, overrideLabel)
            overrideButton.setOnClickListener { onOverrideClicked(item) }
        }
    }
}

class ScheduleAdapter(
    private val onEditClicked: (ScheduleEntity) -> Unit,
    private val onDeleteClicked: (ScheduleEntity) -> Unit,
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {
    private val items = mutableListOf<ScheduleEntity>()

    fun submitList(newItems: List<ScheduleEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.scheduleNameTextView)
        private val summaryView: TextView = itemView.findViewById(R.id.scheduleSummaryTextView)
        private val editButton: MaterialButton = itemView.findViewById(R.id.editScheduleButton)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteScheduleButton)

        fun bind(item: ScheduleEntity) {
            nameView.text = item.name
            summaryView.text = formatScheduleSummary(item)
            editButton.setOnClickListener { onEditClicked(item) }
            deleteButton.setOnClickListener { onDeleteClicked(item) }
        }
    }
}

class DeliveryLogAdapter : RecyclerView.Adapter<DeliveryLogAdapter.DeliveryLogViewHolder>() {
    private val items = mutableListOf<DeliveryLogEntity>()

    fun submitList(newItems: List<DeliveryLogEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_delivery_log, parent, false)
        return DeliveryLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeliveryLogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DeliveryLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.logTitleTextView)
        private val subtitleView: TextView = itemView.findViewById(R.id.logSubtitleTextView)
        private val reasonView: TextView = itemView.findViewById(R.id.logReasonTextView)
        private val bodyView: TextView = itemView.findViewById(R.id.logBodyTextView)

        fun bind(item: DeliveryLogEntity) {
            titleView.text = "${item.appName} • ${item.decision}"
            subtitleView.text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(item.timestamp))
            reasonView.text = itemView.context.getString(R.string.reason_template, item.reason)
            bodyView.text = item.title.ifBlank { item.body }.ifBlank {
                itemView.context.getString(R.string.delivery_log_empty_body)
            }
        }
    }
}

private fun formatScheduleSummary(schedule: ScheduleEntity): String {
    return "${daysMaskToLabel(schedule.daysMask)} • ${minuteLabel(schedule.startMinuteOfDay)}-${minuteLabel(schedule.endMinuteOfDay)}"
}

private fun daysMaskToLabel(daysMask: Int): String {
    val labels = listOf("Su", "M", "Tu", "W", "Th", "F", "Sa")
    return labels.mapIndexedNotNull { index, label ->
        if ((daysMask and (1 shl index)) != 0) label else null
    }.joinToString(" ")
}

private fun minuteLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "%02d:%02d".format(hours, mins)
}

fun nextAction(actionName: String): RuleAction {
    return if (actionName == RuleAction.ALLOW.name) RuleAction.BLOCK else RuleAction.ALLOW
}

fun nextChannelOverride(currentValue: String?): RuleAction? {
    return when (currentValue) {
        null -> RuleAction.ALLOW
        RuleAction.ALLOW.name -> RuleAction.BLOCK
        else -> null
    }
}
