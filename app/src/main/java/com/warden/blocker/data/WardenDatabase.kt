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
    @TypeConverter fun toInterceptMode(value: String): InterceptMode = InterceptMode.valueOf(value)
    @TypeConverter fun fromInterceptMode(mode: InterceptMode): String = mode.name
}

@Database(
    entities = [BlockedItem::class, Schedule::class, AccessGrant::class],
    version = 2,
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
                )
                    // Pre-release: schema is still moving. Replace with real migrations
                    // before the first public release (see SPEC.md).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
