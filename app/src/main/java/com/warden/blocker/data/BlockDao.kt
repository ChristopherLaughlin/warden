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
}
