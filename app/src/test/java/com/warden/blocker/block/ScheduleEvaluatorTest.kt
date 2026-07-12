package com.warden.blocker.block

import com.warden.blocker.data.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleEvaluatorTest {

    private fun calAt(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

    private val weekdays9to5 = Schedule(
        id = 1, name = "work", enabled = true,
        daysMask = 0b0011111, // Mon..Fri
        startMinute = 9 * 60, endMinute = 17 * 60,
    )

    @Test fun activeInsideWeekdayWindow() {
        assertTrue(ScheduleEvaluator.isActive(weekdays9to5, calAt(Calendar.WEDNESDAY, 10, 30)))
    }

    @Test fun inactiveBeforeWindow() {
        assertFalse(ScheduleEvaluator.isActive(weekdays9to5, calAt(Calendar.WEDNESDAY, 8, 59)))
    }

    @Test fun inactiveOnWeekend() {
        assertFalse(ScheduleEvaluator.isActive(weekdays9to5, calAt(Calendar.SATURDAY, 10, 30)))
    }

    @Test fun overnightWindowWrapsPastMidnight() {
        // 22:00 -> 06:00, active on Monday nights.
        val overnight = Schedule(
            id = 2, name = "sleep", enabled = true,
            daysMask = 0b0000001, // Monday only
            startMinute = 22 * 60, endMinute = 6 * 60,
        )
        assertTrue(ScheduleEvaluator.isActive(overnight, calAt(Calendar.MONDAY, 23, 0)))
        // Tuesday 05:00 is still owned by Monday's window.
        assertTrue(ScheduleEvaluator.isActive(overnight, calAt(Calendar.TUESDAY, 5, 0)))
        // Tuesday 07:00 is outside.
        assertFalse(ScheduleEvaluator.isActive(overnight, calAt(Calendar.TUESDAY, 7, 0)))
    }
}
