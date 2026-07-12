package com.warden.blocker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What kind of thing is being blocked. */
enum class BlockType { WEBSITE, APP }

/**
 * How a target is enforced when opened during an active block:
 * - [BLOCK]: hard block — a full-screen wall, no way through.
 * - [PAUSE]: a mindful pause (breathe + intention + a wait) that can grant temporary,
 *   time-boxed access — the ScreenZen-style "delayed gratification" flow.
 */
enum class InterceptMode { BLOCK, PAUSE }

/**
 * A single blocked target.
 * For [BlockType.WEBSITE], [value] is a bare domain (subdomains match too).
 * For [BlockType.APP], [value] is a package name.
 *
 * Limits apply only to [InterceptMode.PAUSE] apps:
 * - [openLimitPerDay]: max temporary-access grants per day (0 = unlimited).
 * - [dailyLimitMinutes]: max foreground minutes/day before it hard-blocks (0 = unlimited).
 * - [cooldownMinutes]: minimum minutes between grants (0 = none).
 */
@Entity(tableName = "blocked_items")
data class BlockedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BlockType,
    val value: String,
    val label: String,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val interceptMode: InterceptMode = InterceptMode.BLOCK,
    val pauseSeconds: Int = 15,
    val customPrompt: String? = null,
    val openLimitPerDay: Int = 0,
    val dailyLimitMinutes: Int = 0,
    val cooldownMinutes: Int = 0,
)

/**
 * A time window during which the blocklist is enforced.
 * [daysMask] is a 7-bit mask, bit 0 = Monday … bit 6 = Sunday.
 * [startMinute]/[endMinute] are minutes-since-midnight. If end <= start the window
 * wraps past midnight.
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
)

/**
 * A record that a mindful pause was overridden, granting temporary access to [itemId]
 * until [expiresAt]. Also the source of truth for per-day open counts and cooldowns.
 */
@Entity(
    tableName = "access_grants",
    indices = [Index("itemId"), Index("grantedAt")],
)
data class AccessGrant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
)
