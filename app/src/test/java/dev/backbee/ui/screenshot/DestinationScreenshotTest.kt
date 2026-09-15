package dev.backbee.ui.screenshot

import android.app.Application
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import androidx.compose.foundation.background
import dev.backbee.ui.theme.backbeeColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.backbee.ui.BackbeeNavHost
import dev.backbee.ui.now.NowScreen
import dev.backbee.ui.now.NowViewModel
import dev.backbee.ui.shelf.ShelfScreen
import dev.backbee.ui.shelf.ShelfViewModel
import dev.backbee.ui.archive.EpisodeDetailScreen
import dev.backbee.ui.archive.EpisodeDetailViewModel
import dev.backbee.ui.downloads.DownloadsScreen
import dev.backbee.ui.downloads.DownloadsViewModel
import dev.backbee.ui.settings.SettingsScreen
import dev.backbee.ui.settings.SettingsViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.backbee.data.db.DownloadEntity
import dev.backbee.data.db.DownloadState
import dev.backbee.data.db.EpisodeEntity
import dev.backbee.data.db.PositionEntity
import dev.backbee.data.db.ShowEntity
import dev.backbee.di.AppContainer
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.archive.ArchiveScreen
import dev.backbee.ui.archive.ArchiveViewModel
import dev.backbee.ui.completion.CompletionScreen
import dev.backbee.ui.completion.CompletionViewModel
import dev.backbee.ui.theme.BackbeeTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Actual destination composables, actual VMs and actual Room queries.
 * AppContainer does not accept a database, so this uses Robolectric's isolated
 * temporary database (not an in-memory database). No reflection or fake screen.
 * Application suppresses BackbeeApp's work scheduling, PlayerConnection stays
 * disconnected, artwork is null, and no refresh/search-directory actions run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DestinationScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var player: PlayerConnection
    private val models = mutableListOf<ViewModel>()

    @Before fun seedLocalArchive() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        container = AppContainer(context)
        player = PlayerConnection(context, container.applicationScope)
        val db = container.database
        db.clearAllTables()
        db.showDao().insert(ShowEntity(
            id = 1L, rssUrl = "https://fixture.invalid/feed.xml", title = "Local fixture archive",
            isActive = true, addedAt = FIXED_DATE, speed = 1.5f,
        ))
        db.episodeDao().insertAll(listOf(
            episode(1L, "The opening chapter"),
            episode(2L, "The middle chapter"),
            episode(3L, "The closing chapter"),
        ))
        db.positionDao().upsert(PositionEntity(1L, 3_600L, FIXED_DATE, played = true, playedAt = FIXED_DATE))
        db.positionDao().upsert(PositionEntity(2L, 900L, FIXED_DATE))
        db.markDao().setStarred(2L, true, FIXED_DATE)
        db.downloadDao().upsert(DownloadEntity(2L, DownloadState.DONE,
            filePath = "/fixture/middle.mp3", bytesTotal = 64_000_000L, bytesDone = 64_000_000L))
        db.downloadDao().upsert(DownloadEntity(3L, DownloadState.FAILED, failureCount = 1))
    }

    @After fun closeFixture() {
        val jobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] } +
            if (::container.isInitialized) listOfNotNull(container.applicationScope.coroutineContext[Job]) else emptyList()
        jobs.forEach { it.cancel() }
        // Cancellation is asynchronous: Room unregisters invalidation observers
        // on its query executor. Closing immediately races their finally blocks.
        awaitFixture { jobs.all { it.isCompleted } }
        if (::player.isInitialized) player.release()
        if (::container.isInitialized) container.database.close()
    }

    @Test fun archiveDestinationDark() = archiveDestination(dark = true)
    @Test fun archiveDestinationLight() = archiveDestination(dark = false)
    @Test fun completionDestinationDark() = completionDestination(dark = true)
    @Test fun completionDestinationLight() = completionDestination(dark = false)

    @Test fun archiveSearchEditsVmFiltersRealRowsAndOpensSelectedEpisode() {
        val vm = ArchiveViewModel(container).also(models::add)
        val opened = mutableListOf<Long>()
        compose.setContent {
            BackbeeTheme(darkTheme = false) {
                ArchiveScreen(vm, player, { opened += it })
            }
        }
        awaitFixture { vm.state.value.totalEpisodes == 3 }
        // Semantics flatten the two field Columns into siblings. The search
        // uses the default IME action; the numeric jump explicitly uses Done.
        compose.onNode(hasSetTextAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Default))
            .performTextInput("closing")
        awaitFixture {
            vm.state.value.query == "closing" && vm.state.value.rows.map { it.id } == listOf(3L)
        }
        compose.onNodeWithText("The middle chapter").assertDoesNotExist()
        compose.onNodeWithText("The closing chapter").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(3L), opened)
            assertEquals("Whole-archive counts survive filtering", 3, vm.state.value.totalEpisodes)
            assertEquals(1, vm.state.value.playedCount)
            assertEquals(1, vm.state.value.starredCount)
        }
    }

    private fun archiveDestination(dark: Boolean) {
        val vm = ArchiveViewModel(container).also(models::add)
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                ArchiveScreen(vm, player, {}, Modifier.testTag("destination"))
            }
        }
        awaitFixture { vm.state.value.totalEpisodes == 3 }
        compose.onNodeWithText("Local fixture archive").assertIsDisplayed()
        compose.onNodeWithText("The middle chapter").assertIsDisplayed()
        compose.onNodeWithTag("destination").captureRoboImage(
            "build/outputs/roborazzi/destination-archive-${if (dark) "dark" else "light"}.png",
        )
    }

    private fun completionDestination(dark: Boolean) {
        runBlocking {
            listOf(2L, 3L).forEach { id ->
                container.database.positionDao().upsert(PositionEntity(id, 3_600L, FIXED_DATE,
                    played = true, playedAt = FIXED_DATE + 86_400_000L))
            }
            container.database.showDao().setCompletedAt(1L, FIXED_DATE + 86_400_000L)
        }
        val vm = CompletionViewModel(container, 1L).also(models::add)
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                CompletionScreen(vm, {}, Modifier.testTag("destination"))
            }
        }
        awaitFixture { !vm.state.value.loading }
        compose.runOnIdle {
            assertEquals(3, vm.state.value.stats?.episodeCount)
            assertEquals(1, vm.state.value.starred.size)
        }
        compose.onNodeWithText("Local fixture archive · 3 episodes").assertIsDisplayed()
        compose.onNodeWithTag("destination").captureRoboImage(
            "build/outputs/roborazzi/destination-completion-${if (dark) "dark" else "light"}.png",
        )
    }

    @Test fun nowDestinationDark() = nowDestination(true)
    @Test fun nowDestinationLight() = nowDestination(false)
    @Test fun shelfDestinationDark() = shelfDestination(true)
    @Test fun shelfDestinationLight() = shelfDestination(false)
    @Test fun episodeDestinationDark() = episodeDestination(true)
    @Test fun episodeDestinationLight() = episodeDestination(false)
    @Test fun downloadsDestinationDark() = downloadsDestination(true)
    @Test fun downloadsDestinationLight() = downloadsDestination(false)
    @Test fun settingsDestinationDark() = settingsDestination(true)
    @Test fun settingsDestinationLight() = settingsDestination(false)

    private fun nowDestination(dark: Boolean) {
        val vm = NowViewModel(container, player).also(models::add)
        destination(dark) { NowScreen(vm, player, {}, {}, {}, it) }
        awaitFixture { !vm.state.value.loading }
        compose.onNodeWithText("Local fixture archive").assertIsDisplayed()
        compose.onNodeWithText("The middle chapter").assertIsDisplayed()
        capture("now", dark)
    }

    @Test fun inactiveShelfActionsRemainVisibleAndHaveMobileTargets() = shelfActions(false)
    @Test fun completedShelfActionsRemainVisibleAndHaveMobileTargets() = shelfActions(true)

    private fun shelfActions(completed: Boolean) {
        runBlocking(Dispatchers.IO) {
            container.database.showDao().clearActive()
            if (completed) container.database.showDao().setCompletedAt(1L, FIXED_DATE)
        }
        val vm = ShelfViewModel(container, player).also(models::add)
        destination(false) { ShelfScreen(vm, {}, it) }
        awaitFixture { vm.entries.value.singleOrNull()?.show?.isActive == false }
        compose.onNodeWithText("Local fixture archive").assertIsDisplayed()
        capture(if (completed) "shelf-completed" else "shelf-inactive", false)
        val actions = if (completed) listOf("Activate", "Recap", "Remove") else listOf("Activate", "Remove")
        actions.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        // Exercise a local, reversible action; Activate schedules real work.
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Remove this show?").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("Remove this show?").assertDoesNotExist()
    }

    private fun shelfDestination(dark: Boolean) {
        val vm = ShelfViewModel(container, player).also(models::add)
        destination(dark) { ShelfScreen(vm, {}, it) }
        awaitFixture { vm.entries.value.size == 1 }
        compose.onNodeWithText("Shelf · 1 show").assertIsDisplayed()
        compose.onNodeWithText("Local fixture archive").assertIsDisplayed()
        compose.onNodeWithText("Fetch feed").assertIsNotEnabled()
        capture("shelf", dark)
    }

    private fun episodeDestination(dark: Boolean) {
        val vm = EpisodeDetailViewModel(container, 2L).also(models::add)
        var backs = 0
        destination(dark) { EpisodeDetailScreen(vm, player, { backs++ }, it) }
        awaitFixture { vm.state.value.row?.id == 2L }
        compose.onNodeWithText("The middle chapter").assertIsDisplayed()
        compose.onNodeWithText("Resume from 15:00").assertIsDisplayed()
        capture("episode", dark)
        compose.onNodeWithText("← Archive").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun downloadsDestination(dark: Boolean) {
        val vm = DownloadsViewModel(container).also(models::add)
        destination(dark) { DownloadsScreen(vm, it) }
        awaitFixture { vm.state.value.onDevice.size == 1 && vm.state.value.failed.size == 1 }
        compose.onNodeWithText("Storage").assertIsDisplayed()
        compose.onNodeWithText("The closing chapter").assertIsDisplayed()
        compose.onNodeWithText("The middle chapter").assertIsDisplayed()
        capture("downloads", dark)
    }

    private fun settingsDestination(dark: Boolean) {
        val vm = SettingsViewModel(container, player).also(models::add)
        destination(dark) { SettingsScreen(vm, it) }
        awaitFixture { vm.state.value.activeShow?.id == 1L }
        compose.onNodeWithText("Per show — Local fixture archive").assertIsDisplayed()
        compose.onNodeWithText("Playback speed").assertIsDisplayed()
        capture("settings", dark)
        // Also capture the lower settings sections, not only the first viewport.
        compose.onNodeWithTag("destination").performScrollToNode(hasText("Reset to defaults"))
        compose.onNodeWithText("Rewind on resume").assertIsDisplayed()
        compose.onNodeWithText("Reset to defaults").assertIsDisplayed()
        capture("settings-rewind", dark)
        compose.onNodeWithTag("destination").performScrollToNode(hasText("Restore from a backup file"))
        compose.onNodeWithText("Restore from a backup file").assertIsDisplayed()
        capture("settings-backup", dark)
        compose.onNodeWithTag("destination").performScrollToNode(hasText("Privacy policy"))
        compose.onNodeWithText("About").assertIsDisplayed()
        capture("settings-about", dark)
    }

    @Test fun navigationTabsAndEpisodeBackDark() = navigationTabsAndEpisodeBack(true)
    @Test fun navigationTabsAndEpisodeBackLight() = navigationTabsAndEpisodeBack(false)

    private fun navigationTabsAndEpisodeBack(dark: Boolean) {
        compose.setContent { BackbeeTheme(darkTheme = dark) { BackbeeNavHost(container, player) } }
        fun captureNavigation(name: String) {
            compose.onRoot().captureRoboImage(
                "build/outputs/roborazzi/navigation-$name-${if (dark) "dark" else "light"}.png",
            )
        }
        fun tab(label: String) = compose.onNode(hasContentDescription(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        fun waitForText(text: String) = awaitFixture {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        waitForText("The middle chapter")
        tab("Now").assertIsSelected()
        captureNavigation("now")
        tab("Archive").performClick()
        waitForText("Search titles")
        tab("Archive").assertIsSelected()
        waitForText("The middle chapter")
        compose.onNodeWithText("The middle chapter").assertIsDisplayed()
        captureNavigation("archive")
        compose.onNodeWithText("The middle chapter").performClick()
        waitForText("← Archive")
        compose.onNodeWithText("Resume from 15:00").assertIsDisplayed()
        captureNavigation("episode")
        compose.onNodeWithText("← Archive").performClick()
        waitForText("Search titles")
        tab("Shelf").performClick()
        waitForText("Shelf · 1 show")
        tab("Shelf").assertIsSelected()
        captureNavigation("shelf")
        tab("Downloads").performClick()
        waitForText("Storage")
        tab("Downloads").assertIsSelected()
        waitForText("The closing chapter")
        waitForText("The middle chapter")
        compose.onNodeWithText("The middle chapter").assertIsDisplayed()
        captureNavigation("downloads")
        tab("Settings").performClick()
        waitForText("Per show — Local fixture archive")
        tab("Settings").assertIsSelected()
        captureNavigation("settings")
        tab("Now").performClick()
        waitForText("The middle chapter")
        tab("Now").assertIsSelected()
    }

    private fun destination(dark: Boolean, content: @Composable (Modifier) -> Unit) {
        // BackbeeNavHost supplies the page background in production. NowScreen
        // intentionally relies on it; provide that same host surface here rather
        // than recording light-theme text over a transparent (black) image.
        compose.setContent {
            BackbeeTheme(darkTheme = dark) {
                content(Modifier.background(backbeeColors.bgPage).testTag("destination"))
            }
        }
    }

    private fun capture(name: String, dark: Boolean) {
        compose.onNodeWithTag("destination").captureRoboImage(
            "build/outputs/roborazzi/destination-$name-${if (dark) "dark" else "light"}.png",
        )
    }

    private fun awaitFixture(condition: () -> Boolean) {
        // Room completion resumes viewModelScope on Android's main Handler.
        // Advancing Compose's frame clock alone does not drain that paused
        // Robolectric looper (including CompletionViewModel's eager init load).
        // Keep real lifecycle subscriptions and real dispatchers, and drain both.
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun episode(id: Long, title: String) = EpisodeEntity(
        id = id, showId = 1L, guid = "fixture-$id", orderIndex = id.toInt() - 1,
        title = title, pubDate = FIXED_DATE, durationSeconds = 3_600L,
        enclosureUrl = "https://fixture.invalid/$id.mp3", episodeNumber = id.toInt(),
    )

    private companion object { const val FIXED_DATE = 1_615_680_000_000L }
}
