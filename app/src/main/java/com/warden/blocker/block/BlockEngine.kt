package com.warden.blocker.block

import com.warden.blocker.data.BlockRepository
import com.warden.blocker.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Answers "is blocking enforced right now?" from the master switch + schedules. The VPN
 * asks it for [blockedDomains]; per-app decisions go through [AccessController].
 */
class BlockEngine(
    private val repo: BlockRepository,
    private val settings: SettingsStore,
) {
    /**
     * True when blocking is enforced right now. A focus session forces it on for its
     * duration; otherwise it's the master switch AND (always-on OR an active schedule).
     */
    suspend fun isBlockingActiveNow(now: Calendar = Calendar.getInstance()): Boolean {
        if (settings.focusEndsAt.first() > now.timeInMillis) return true
        if (!settings.masterEnabled.first()) return false
        if (settings.alwaysOn.first()) return true
        return ScheduleEvaluator.anyActive(repo.enabledSchedules(), now)
    }

    suspend fun blockedDomains(): List<String> =
        if (isBlockingActiveNow()) repo.enabledDomains() else emptyList()
}
