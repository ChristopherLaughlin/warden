package com.warden.blocker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.RoomDatabase

class Converters {
    @TypeConverter fun toBlockType(value: String): BlockType = BlockType.valueOf(value)
    @TypeConverter fun fromBlockType(type: BlockType): String = type.name
}

@Database(
    entities = [BlockedItem::class, Schedule::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class WardenDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao

    companion object {
        @Volatile private var instance: WardenDatabase? = null

        fun get(context: Context): WardenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardenDatabase::class.java,
                    "warden.db",
                ).build().also { instance = it }
            }
    }
}
