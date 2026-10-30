package dev.backbee.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.backbee.core.playback.ListeningDirection
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The v2 -> v3 migration in the shape that matters: an existing install with a
 * bookmark and a played flag opens after the upgrade with both intact, and a
 * show that predates the direction column comes back as oldest-first.
 *
 * This builds a real v2 file from the committed schema rather than asking Room
 * to create one, because the point is what happens to data that is already
 * there - a destructive migration would pass every other test in this suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var file: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V2_CREATE.forEach(db::execSQL)
            db.execSQL(
                """
                INSERT INTO shows
                    (id, rss_url, title, artwork_url, is_active, speed, skip_intro_s,
                     skip_outro_s, added_at, completed_at, last_refreshed_at)
                VALUES
                    (1, 'https://example.com/feed', 'The Long Archive', NULL, 1, 1.6, 15,
                     30, 1000, NULL, 2000)
                """
            )
            db.execSQL(
                """
                INSERT INTO episodes
                    (id, show_id, guid, order_index, title, pub_date, duration_s,
                     enclosure_url, description, episode_number, enclosure_bytes)
                VALUES
                    (1, 1, 'oldest', 0, 'Episode one', 100, 600, 'https://cdn.example/0.mp3', NULL, 1, 100),
                    (2, 1, 'newest', 1, 'Episode two', 200, 600, 'https://cdn.example/1.mp3', NULL, 2, 100)
                """
            )
            db.execSQL(
                """
                INSERT INTO positions (episode_id, position_s, updated_at, played, played_at)
                VALUES (1, 123, 3000, 0, NULL),
                       (2, 600, 4000, 1, 4000)
                """
            )
            db.version = 2
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migration to v3 adds direction without touching positions or played flags`() = runTest {
        val db = Room.databaseBuilder(context, BackbeeDatabase::class.java, DB_NAME)
            .addMigrations(BackbeeDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val show = db.showDao().getById(1)
            assertThat(show?.direction).isEqualTo(ListeningDirection.OLDEST_FIRST)
            // The user's per-show settings survive the schema change too.
            assertThat(show?.speed).isWithin(0.001f).of(1.6f)
            assertThat(show?.skipIntroSeconds).isEqualTo(15)
            assertThat(show?.skipOutroSeconds).isEqualTo(30)

            assertThat(db.positionDao().get(1)?.positionSeconds).isEqualTo(123)
            assertThat(db.positionDao().get(2)?.played).isTrue()
            assertThat(db.episodeDao().nextUnplayed(1, newestFirst = false)?.id).isEqualTo(1)
            // Episode two is the only finished one, so both directions land on 1.
            assertThat(db.episodeDao().nextUnplayed(1, newestFirst = true)?.id).isEqualTo(1)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a migrated show can be switched to newest first and walks the other way`() = runTest {
        val db = Room.databaseBuilder(context, BackbeeDatabase::class.java, DB_NAME)
            .addMigrations(BackbeeDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            // Re-open both: the older one finished, the newest one unplayed.
            db.positionDao().markPlayed(1, 600, 5000)
            db.positionDao().markUnplayed(2, 5000)

            val newestFirst = db.episodeDao().nextUnplayed(1, newestFirst = true)
            assertThat(newestFirst?.id).isEqualTo(2)

            db.showDao().setDirection(1, ListeningDirection.NEWEST_FIRST)
            assertThat(db.showDao().getById(1)?.direction).isEqualTo(ListeningDirection.NEWEST_FIRST)
            // Flipping did not rewrite anything about the old episode.
            assertThat(db.positionDao().get(1)?.played).isTrue()
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"

        /**
         * The committed v2 schema, verbatim from
         * app/schemas/dev.backbee.data.db.BackbeeDatabase/2.json.
         */
        val V2_CREATE = listOf(
            "CREATE TABLE IF NOT EXISTS `shows` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rss_url` TEXT NOT NULL, `title` TEXT NOT NULL, `artwork_url` TEXT, `is_active` INTEGER NOT NULL, `speed` REAL NOT NULL, `skip_intro_s` INTEGER NOT NULL, `skip_outro_s` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, `completed_at` INTEGER, `last_refreshed_at` INTEGER)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_shows_rss_url` ON `shows` (`rss_url`)",
            "CREATE TABLE IF NOT EXISTS `episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `show_id` INTEGER NOT NULL, `guid` TEXT NOT NULL, `order_index` INTEGER NOT NULL, `title` TEXT NOT NULL, `pub_date` INTEGER, `duration_s` INTEGER, `enclosure_url` TEXT, `description` TEXT, `episode_number` INTEGER, `enclosure_bytes` INTEGER, FOREIGN KEY(`show_id`) REFERENCES `shows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_show_id_guid` ON `episodes` (`show_id`, `guid`)",
            "CREATE INDEX IF NOT EXISTS `index_episodes_show_id_order_index` ON `episodes` (`show_id`, `order_index`)",
            "CREATE TABLE IF NOT EXISTS `positions` (`episode_id` INTEGER NOT NULL, `position_s` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `played` INTEGER NOT NULL, `played_at` INTEGER, PRIMARY KEY(`episode_id`), FOREIGN KEY(`episode_id`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `marks` (`episode_id` INTEGER NOT NULL, `starred` INTEGER NOT NULL, `note` TEXT, `keep_after_playing` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`episode_id`), FOREIGN KEY(`episode_id`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `downloads` (`episode_id` INTEGER NOT NULL, `state` TEXT NOT NULL, `file_path` TEXT, `bytes_total` INTEGER NOT NULL, `bytes_done` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `failure_count` INTEGER NOT NULL, PRIMARY KEY(`episode_id`), FOREIGN KEY(`episode_id`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
