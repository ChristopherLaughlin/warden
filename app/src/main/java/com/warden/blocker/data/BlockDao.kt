package com.warden.blocker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {

    // --- Blocked items ---
    @Query("SELECT * FROM blocked_items ORDER BY label COLLATE NOCASE")
    fun observeItems(): Flow<List<BlockedItem>>

    @Query("SELECT * FROM blocked_items WHERE type = :type AND enabled = 1")
    suspend fun enabledItemsOfType(type: BlockType): List<BlockedItem>

    @Query("SELECT * FROM blocked_items WHERE type = :type AND value = :value LIMIT 1")
    suspend fun itemByValue(type: BlockType, value: String): BlockedItem?

    @Query("SELECT * FROM blocked_items WHERE id = :id LIMIT 1")
    suspend fun itemById(id: Long): BlockedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: BlockedItem): Long

    @Update
    suspend fun updateItem(item: BlockedItem)

    @Delete
    suspend fun deleteItem(item: BlockedItem)

    // --- Schedules ---
    @Query("SELECT * FROM schedules ORDER BY name COLLATE NOCASE")
    fun observeSchedules(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun enabledSchedules(): List<Schedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: Schedule): Long

    @Delete
    suspend fun deleteSchedule(schedule: Schedule)

    // --- Access grants (temporary passes / open counting / cooldown) ---
    @Insert
    suspend fun insertGrant(grant: AccessGrant): Long

    @Query("SELECT * FROM access_grants WHERE itemId = :itemId AND expiresAt > :now ORDER BY expiresAt DESC LIMIT 1")
    suspend fun activeGrant(itemId: Long, now: Long): AccessGrant?

    @Query("SELECT COUNT(*) FROM access_grants WHERE itemId = :itemId AND grantedAt >= :sinceMillis")
    suspend fun grantCountSince(itemId: Long, sinceMillis: Long): Int

    @Query("SELECT MAX(grantedAt) FROM access_grants WHERE itemId = :itemId")
    suspend fun lastGrantAt(itemId: Long): Long?

    @Query("DELETE FROM access_grants WHERE grantedAt < :beforeMillis")
    suspend fun pruneGrantsBefore(beforeMillis: Long)
}
