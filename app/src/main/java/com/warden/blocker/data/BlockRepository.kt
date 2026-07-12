package com.warden.blocker.data

import kotlinx.coroutines.flow.Flow

/** Single access point for blocklist + schedule persistence. */
class BlockRepository(private val dao: BlockDao) {

    val items: Flow<List<BlockedItem>> = dao.observeItems()
    val schedules: Flow<List<Schedule>> = dao.observeSchedules()

    suspend fun enabledDomains(): List<String> =
        dao.enabledItemsOfType(BlockType.WEBSITE).map { it.value.lowercase().removePrefix("www.") }

    suspend fun enabledPackages(): Set<String> =
        dao.enabledItemsOfType(BlockType.APP).map { it.value }.toSet()

    suspend fun addItem(item: BlockedItem) = dao.upsertItem(item)
    suspend fun updateItem(item: BlockedItem) = dao.updateItem(item)
    suspend fun removeItem(item: BlockedItem) = dao.deleteItem(item)

    suspend fun enabledSchedules(): List<Schedule> = dao.enabledSchedules()
    suspend fun addSchedule(schedule: Schedule) = dao.upsertSchedule(schedule)
    suspend fun removeSchedule(schedule: Schedule) = dao.deleteSchedule(schedule)
}
