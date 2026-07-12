package com.warden.blocker.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/** Single access point for blocklist + schedule + grant persistence. */
class BlockRepository(private val dao: BlockDao) {

    val items: Flow<List<BlockedItem>> = dao.observeItems()
    val schedules: Flow<List<Schedule>> = dao.observeSchedules()

    suspend fun enabledDomains(): List<String> =
        dao.enabledItemsOfType(BlockType.WEBSITE).map { it.value.lowercase().removePrefix("www.") }

    suspend fun enabledApps(): List<BlockedItem> = dao.enabledItemsOfType(BlockType.APP)

    suspend fun appItem(packageName: String): BlockedItem? =
        dao.itemByValue(BlockType.APP, packageName)

    suspend fun item(id: Long): BlockedItem? = dao.itemById(id)

    suspend fun addItem(item: BlockedItem) = dao.upsertItem(item)
    suspend fun updateItem(item: BlockedItem) = dao.updateItem(item)
    suspend fun removeItem(item: BlockedItem) = dao.deleteItem(item)

    suspend fun enabledSchedules(): List<Schedule> = dao.enabledSchedules()
    suspend fun addSchedule(schedule: Schedule) = dao.upsertSchedule(schedule)
    suspend fun removeSchedule(schedule: Schedule) = dao.deleteSchedule(schedule)

    // --- Grants ---
    suspend fun activeGrant(itemId: Long, now: Long): AccessGrant? = dao.activeGrant(itemId, now)

    suspend fun opensToday(itemId: Long): Int = dao.grantCountSince(itemId, startOfToday())

    suspend fun lastGrantAt(itemId: Long): Long? = dao.lastGrantAt(itemId)

    suspend fun grantAccess(item: BlockedItem, durationMinutes: Int): AccessGrant {
        val now = System.currentTimeMillis()
        val grant = AccessGrant(
            itemId = item.id,
            packageName = item.value,
            grantedAt = now,
            expiresAt = now + durationMinutes * 60_000L,
        )
        dao.insertGrant(grant)
        dao.pruneGrantsBefore(now - 30L * 24 * 60 * 60 * 1000) // keep ~30 days
        return grant
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
