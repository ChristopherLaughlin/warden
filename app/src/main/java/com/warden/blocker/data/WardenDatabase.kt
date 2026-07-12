package com.warden.blocker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun toBlockType(value: String): BlockType = BlockType.valueOf(value)
    @TypeConverter fun fromBlockType(type: BlockType): String = type.name
    @TypeConverter fun toInterceptMode(value: String): InterceptMode = InterceptMode.valueOf(value)
    @TypeConverter fun fromInterceptMode(mode: InterceptMode): String = mode.name
}

@Database(
    entities = [BlockedItem::class, Schedule::class, AccessGrant::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WardenDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao

    companion object {
        @Volatile private var instance: WardenDatabase? = null

        /**
         * v1 → v2: add the intercept-mode / limit columns to blocked_items and create the
         * access_grants table. Column/table/index shapes match what Room generates for the
         * v2 entities so the schema validates on open — existing blocklists survive upgrades.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN interceptMode TEXT NOT NULL DEFAULT 'BLOCK'")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN pauseSeconds INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN customPrompt TEXT")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN openLimitPerDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN dailyLimitMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_items ADD COLUMN cooldownMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS access_grants (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "itemId INTEGER NOT NULL, packageName TEXT NOT NULL, " +
                        "grantedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_access_grants_itemId ON access_grants (itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_access_grants_grantedAt ON access_grants (grantedAt)")
            }
        }

        fun get(context: Context): WardenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardenDatabase::class.java,
                    "warden.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    // Only wipe on a downgrade (dev rollback); real upgrades migrate cleanly.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
