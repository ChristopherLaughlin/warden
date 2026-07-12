package com.warden.blocker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What kind of thing is being blocked. */
enum class BlockType { WEBSITE, APP }

/**
 * A single blocked target.
 * For [BlockType.WEBSITE], [value] is a bare domain, e.g. "facebook.com" (subdomains match too).
 * For [BlockType.APP], [value] is a package name, e.g. "com.instagram.android".
 */
@Entity(tableName = "blocked_items")
data class BlockedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BlockType,
    val value: String,
    val label: String,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
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
