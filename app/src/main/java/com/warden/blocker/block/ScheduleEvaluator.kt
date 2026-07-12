package com.warden.blocker.block

import com.warden.blocker.data.Schedule
import java.util.Calendar

/** Pure functions deciding whether a schedule is active at a given instant. */
object ScheduleEvaluator {

    /** bit 0 = Monday … bit 6 = Sunday. */
    fun isActive(schedule: Schedule, cal: Calendar): Boolean {
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Calendar.MONDAY == 2 … SUNDAY == 1. Map to 0..6 with Monday = 0.
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0 … Sun=6
        val dayMatches = (schedule.daysMask shr dow) and 1 == 1
        if (!dayMatches && !wrapsFromPreviousDay(schedule, minuteOfDay, dow)) return false
        return withinWindow(schedule, minuteOfDay, dow)
    }

    private fun withinWindow(s: Schedule, minute: Int, dow: Int): Boolean {
        val todayOn = (s.daysMask shr dow) and 1 == 1
        return if (s.endMinute > s.startMinute) {
            todayOn && minute >= s.startMinute && minute < s.endMinute
        } else {
            // Wraps past midnight: active late today OR early "tomorrow" (owned by prev day).
            (todayOn && minute >= s.startMinute) ||
                (((s.daysMask shr ((dow + 6) % 7)) and 1 == 1) && minute < s.endMinute)
        }
    }

    private fun wrapsFromPreviousDay(s: Schedule, minute: Int, dow: Int): Boolean =
        s.endMinute <= s.startMinute &&
            ((s.daysMask shr ((dow + 6) % 7)) and 1 == 1) &&
            minute < s.endMinute

    fun anyActive(schedules: List<Schedule>, cal: Calendar): Boolean =
        schedules.any { it.enabled && isActive(it, cal) }
}
