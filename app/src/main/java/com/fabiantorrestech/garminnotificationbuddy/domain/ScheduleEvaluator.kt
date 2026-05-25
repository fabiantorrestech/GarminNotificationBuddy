package com.fabiantorrestech.garminnotificationbuddy.domain

import com.fabiantorrestech.garminnotificationbuddy.data.ScheduleEntity
import com.fabiantorrestech.garminnotificationbuddy.model.minuteOfDay
import java.time.DayOfWeek
import java.time.ZonedDateTime

class ScheduleEvaluator {
    fun anyActiveBlockingSchedule(
        schedules: List<ScheduleEntity>,
        now: ZonedDateTime,
    ): Boolean {
        return schedules.any { it.isEnabled && isActive(it, now) }
    }

    fun activeSchedules(
        schedules: List<ScheduleEntity>,
        now: ZonedDateTime,
    ): List<ScheduleEntity> {
        return schedules.filter { it.isEnabled && isActive(it, now) }
    }

    fun isActive(schedule: ScheduleEntity, now: ZonedDateTime): Boolean {
        val currentMinute = now.minuteOfDay()
        val start = schedule.startMinuteOfDay
        val end = schedule.endMinuteOfDay
        val todayEnabled = isDayEnabled(schedule.daysMask, now.dayOfWeek)

        if (start == end) {
            return todayEnabled
        }

        return if (start < end) {
            todayEnabled && currentMinute in start until end
        } else {
            val previousDay = now.dayOfWeek.minus(1)
            (todayEnabled && currentMinute >= start) ||
                (isDayEnabled(schedule.daysMask, previousDay) && currentMinute < end)
        }
    }

    private fun isDayEnabled(daysMask: Int, dayOfWeek: DayOfWeek): Boolean {
        val bitIndex = dayOfWeek.value % 7
        return (daysMask and (1 shl bitIndex)) != 0
    }
}

private fun DayOfWeek.minus(days: Int): DayOfWeek {
    val adjusted = (ordinal - (days % 7) + 7) % 7
    return DayOfWeek.entries[adjusted]
}
