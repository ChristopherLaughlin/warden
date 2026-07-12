package com.warden.blocker.block

import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.InterceptMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessControllerTest {

    private fun app(
        mode: InterceptMode = InterceptMode.PAUSE,
        enabled: Boolean = true,
        openLimit: Int = 0,
        timeLimit: Int = 0,
        cooldown: Int = 0,
    ) = BlockedItem(
        id = 1, type = BlockType.APP, value = "com.x", label = "X", enabled = enabled,
        interceptMode = mode, openLimitPerDay = openLimit, dailyLimitMinutes = timeLimit, cooldownMinutes = cooldown,
    )

    private val now = 1_000_000_000L

    @Test fun disabledItemIsAllowed() {
        assertEquals(AccessDecision.Allow, AccessController.decide(app(enabled = false), false, 0, null, 0, now))
    }

    @Test fun activeGrantAllows() {
        assertEquals(AccessDecision.Allow, AccessController.decide(app(), hasActiveGrant = true, 0, null, 0, now))
    }

    @Test fun blockModeHardBlocks() {
        val d = AccessController.decide(app(mode = InterceptMode.BLOCK), false, 0, null, 0, now)
        assertTrue(d is AccessDecision.HardBlock)
    }

    @Test fun pauseWhenNoLimits() {
        val d = AccessController.decide(app(), false, 0, null, 0, now)
        assertTrue(d is AccessDecision.Pause)
    }

    @Test fun openLimitReached() {
        val d = AccessController.decide(app(openLimit = 3), false, opensToday = 3, null, 0, now)
        assertEquals(LimitReason.OPENS, (d as AccessDecision.LimitReached).reason)
    }

    @Test fun underOpenLimitPauses() {
        val d = AccessController.decide(app(openLimit = 3), false, opensToday = 2, null, 0, now)
        assertTrue(d is AccessDecision.Pause)
    }

    @Test fun timeLimitReachedTakesPriority() {
        // Over time limit AND over open limit → TIME is checked first.
        val d = AccessController.decide(app(openLimit = 1, timeLimit = 30), false, opensToday = 5, null, usedMinutesToday = 45, now)
        assertEquals(LimitReason.TIME, (d as AccessDecision.LimitReached).reason)
    }

    @Test fun cooldownActive() {
        val last = now - 60_000L // 1 min ago
        val d = AccessController.decide(app(cooldown = 5), false, 0, lastGrantAt = last, 0, now)
        assertEquals(LimitReason.COOLDOWN, (d as AccessDecision.LimitReached).reason)
    }

    @Test fun cooldownElapsedPauses() {
        val last = now - 6 * 60_000L // 6 min ago, cooldown 5 min
        val d = AccessController.decide(app(cooldown = 5), false, 0, lastGrantAt = last, 0, now)
        assertTrue(d is AccessDecision.Pause)
    }
}
