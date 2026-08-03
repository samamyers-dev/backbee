package dev.backbee.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

class Converters {
    @TypeConverter
    fun toDownloadState(value: String?): DownloadState? =
        value?.let { runCatching { DownloadState.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromDownloadState(state: DownloadState?): String? = state?.name
}

@Database(
    entities = [
        ShowEntity::class,
        EpisodeEntity::class,
        PositionEntity::class,
        MarkEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BackbeeDatabase : RoomDatabase() {

    abstract fun showDao(): ShowDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun positionDao(): PositionDao
    abstract fun markDao(): MarkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "backbee.db"

        /**
         * Cascading deletes only fire when foreign keys are switched on, and Room
         * leaves them off by default. Exposed so tests open the database the same
         * way the app does rather than testing a subtly different schema.
         */
        val ENABLE_FOREIGN_KEYS = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        /**
         * Adds marks.keep_after_playing. A migration rather than a destructive
         * rebuild because the database holds the one thing the app must never
         * lose - every position in the archive.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE marks ADD COLUMN keep_after_playing INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun build(context: Context): BackbeeDatabase =
            Room.databaseBuilder(context.applicationContext, BackbeeDatabase::class.java, NAME)
                .addCallback(ENABLE_FOREIGN_KEYS)
                .addMigrations(MIGRATION_1_2)
                .build()

        fun fileFor(context: Context): File = context.getDatabasePath(NAME)
    }
}
