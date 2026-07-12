package com.warden.blocker.block

import com.warden.blocker.data.BlockRepository
import com.warden.blocker.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Central authority answering "is blocking enforced right now, and for what?".
 * Consulted by the VPN (domains) and the accessibility service (packages).
 */
class BlockEngine(
    private val repo: BlockRepository,
    private val settings: SettingsStore,
) {
    /** True when the master switch is on AND (always-on OR a schedule is currently active). */
    suspend fun isBlockingActiveNow(now: Calendar = Calendar.getInstance()): Boolean {
        if (!settings.masterEnabled.first()) return false
        if (settings.alwaysOn.first()) return true
        return ScheduleEvaluator.anyActive(repo.enabledSchedules(), now)
    }

    suspend fun blockedDomains(): List<String> =
        if (isBlockingActiveNow()) repo.enabledDomains() else emptyList()

    suspend fun isPackageBlockedNow(packageName: String): Boolean =
        isBlockingActiveNow() && repo.enabledPackages().contains(packageName)
}
