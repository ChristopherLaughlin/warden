package com.warden.blocker.block

import com.warden.blocker.data.BlockRepository
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.InterceptMode

enum class LimitReason { OPENS, TIME, COOLDOWN }

/** What should happen when a blocked app is opened right now. */
sealed interface AccessDecision {
    /** Let the app through (not blocked, or a temporary grant is active). */
    data object Allow : AccessDecision
    /** Show the mindful pause; the user may earn timed access. */
    data class Pause(val item: BlockedItem) : AccessDecision
    /** Hard wall — no way through. */
    data class HardBlock(val item: BlockedItem) : AccessDecision
    /** A pause app that has hit a limit; access denied until it resets. */
    data class LimitReached(val item: BlockedItem, val reason: LimitReason) : AccessDecision
}

/**
 * Central authority for per-app access decisions. Combines the schedule/master state
 * (via [BlockEngine]) with per-item limits and any active grant.
 *
 * [usageMinutesToday] returns the app's foreground minutes today (from UsageStats), used
 * to enforce daily time limits.
 */
class AccessController(
    private val repo: BlockRepository,
    private val engine: BlockEngine,
    private val usageMinutesToday: suspend (String) -> Long,
) {
    suspend fun decideForPackage(packageName: String): AccessDecision {
        if (!engine.isBlockingActiveNow()) return AccessDecision.Allow
        val item = repo.appItem(packageName) ?: return AccessDecision.Allow
        if (!item.enabled) return AccessDecision.Allow

        val now = System.currentTimeMillis()
        if (repo.activeGrant(item.id, now) != null) return AccessDecision.Allow

        if (item.interceptMode == InterceptMode.BLOCK) return AccessDecision.HardBlock(item)

        // PAUSE mode: enforce limits before offering the pause.
        if (item.dailyLimitMinutes > 0 && usageMinutesToday(packageName) >= item.dailyLimitMinutes) {
            return AccessDecision.LimitReached(item, LimitReason.TIME)
        }
        if (item.openLimitPerDay > 0 && repo.opensToday(item.id) >= item.openLimitPerDay) {
            return AccessDecision.LimitReached(item, LimitReason.OPENS)
        }
        if (item.cooldownMinutes > 0) {
            val last = repo.lastGrantAt(item.id)
            if (last != null && now - last < item.cooldownMinutes * 60_000L) {
                return AccessDecision.LimitReached(item, LimitReason.COOLDOWN)
            }
        }
        return AccessDecision.Pause(item)
    }
}
