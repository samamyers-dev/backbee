package dev.backbee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.backbee.di.AppContainer
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.archive.ArchiveScreen
import dev.backbee.ui.archive.ArchiveViewModel
import dev.backbee.ui.archive.EpisodeDetailScreen
import dev.backbee.ui.archive.EpisodeDetailViewModel
import dev.backbee.ui.completion.CompletionScreen
import dev.backbee.ui.completion.CompletionViewModel
import dev.backbee.ui.components.BrutalDivider
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.NowPlayingBar
import dev.backbee.ui.downloads.DownloadsScreen
import dev.backbee.ui.downloads.DownloadsViewModel
import dev.backbee.ui.now.NowScreen
import dev.backbee.ui.now.NowViewModel
import dev.backbee.ui.settings.SettingsScreen
import dev.backbee.ui.settings.SettingsViewModel
import dev.backbee.ui.shelf.ShelfScreen
import dev.backbee.ui.shelf.ShelfViewModel
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors

private object Routes {
    const val NOW = "now"
    const val ARCHIVE = "archive"
    const val SHELF = "shelf"
    const val EPISODE = "episode/{episodeId}"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val COMPLETION = "completion/{showId}"

    fun episode(id: Long) = "episode/$id"
    fun completion(showId: Long) = "completion/$showId"
}

/** The three tabs from the spec. Everything else is reached from inside them. */
private val TABS = listOf(
    Routes.NOW to "NOW",
    Routes.ARCHIVE to "ARCHIVE",
    Routes.SHELF to "SHELF",
)

@Composable
fun BackbeeNavHost(
    container: AppContainer,
    player: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val playerState by player.state.collectAsStateWithLifecycle()
    val colors = backbeeColors

    Column(modifier.fillMaxSize().background(colors.bgPage)) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Routes.NOW,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                composable(Routes.NOW) {
                    val vm: NowViewModel = viewModel(factory = viewModelFactory { NowViewModel(container, player) })
                    NowScreen(
                        viewModel = vm,
                        player = player,
                        onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                        onOpenShelf = { navController.navigate(Routes.SHELF) },
                        onOpenCompletion = { navController.navigate(Routes.completion(it)) },
                    )
                }

                composable(Routes.ARCHIVE) {
                    val vm: ArchiveViewModel = viewModel(factory = viewModelFactory { ArchiveViewModel(container) })
                    ArchiveScreen(
                        viewModel = vm,
                        player = player,
                        onOpenEpisode = { navController.navigate(Routes.episode(it)) },
                    )
                }

                composable(Routes.SHELF) {
                    val vm: ShelfViewModel = viewModel(factory = viewModelFactory { ShelfViewModel(container) })
                    ShelfScreen(
                        viewModel = vm,
                        onOpenCompletion = { navController.navigate(Routes.completion(it)) },
                    )
                }

                composable(
                    route = Routes.EPISODE,
                    arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
                ) { entry ->
                    val episodeId = entry.arguments?.getLong("episodeId") ?: return@composable
                    val vm: EpisodeDetailViewModel =
                        viewModel(factory = viewModelFactory { EpisodeDetailViewModel(container, episodeId) })
                    EpisodeDetailScreen(
                        viewModel = vm,
                        player = player,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SETTINGS) {
                    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
                    SettingsScreen(viewModel = vm)
                }

                composable(Routes.DOWNLOADS) {
                    val vm: DownloadsViewModel = viewModel(factory = viewModelFactory { DownloadsViewModel(container) })
                    DownloadsScreen(viewModel = vm)
                }

                composable(
                    route = Routes.COMPLETION,
                    arguments = listOf(navArgument("showId") { type = NavType.LongType }),
                ) { entry ->
                    val showId = entry.arguments?.getLong("showId") ?: return@composable
                    val vm: CompletionViewModel =
                        viewModel(factory = viewModelFactory { CompletionViewModel(container, showId) })
                    CompletionScreen(
                        viewModel = vm,
                        onActivatedNextShow = { navController.popBackStack(Routes.NOW, inclusive = false) },
                    )
                }
            }
        }

        // Outside the NavHost on purpose: this is the one surface that has to
        // survive every tab switch, so it cannot live inside a destination.
        if (playerState.hasEpisode) {
            BrutalDivider(thickness = Stroke.thick)
            NowPlayingBar(
                title = playerState.title.orEmpty(),
                subtitle = playerState.subtitle,
                artworkUrl = playerState.artworkUrl,
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                onOpen = {
                    if (currentRoute != Routes.NOW) {
                        navController.navigate(Routes.NOW) {
                            popUpTo(Routes.NOW) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onTogglePlay = player::togglePlayPause,
                onSkipBack = player::skipBack,
            )
        }

        BrutalDivider(thickness = Stroke.thick)
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .navigationBarsPadding(),
        ) {
            TABS.forEach { (route, label) ->
                val selected = currentRoute == route
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!selected) {
                                navController.navigate(route) {
                                    // Tabs are destinations, not a stack: going
                                    // back from any of them leaves the app.
                                    popUpTo(Routes.NOW) { inclusive = route == Routes.NOW }
                                    launchSingleTop = true
                                }
                            }
                        }
                        .background(if (selected) colors.accentPrimary else colors.bgPanel)
                        .padding(vertical = 18.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = selected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Mono(
                        text = label,
                        style = BackbeeType.label,
                        color = if (selected) colors.onAccentPrimary else colors.textMuted,
                    )
                }
            }
            // Settings and Downloads are rare surfaces, so they get a narrow
            // slot rather than a tab of their own.
            // Both slots are a bare glyph, which a screen reader either skips or
            // reads as punctuation, so each carries its name instead.
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .clickable { navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true } }
                    .padding(vertical = 18.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Downloads"
                        role = Role.Tab
                    },
                contentAlignment = Alignment.Center,
            ) {
                Mono("▼", style = BackbeeType.label, color = backbeeColors.textMuted)
            }
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .clickable { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
                    .padding(vertical = 18.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Settings"
                        role = Role.Tab
                    },
                contentAlignment = Alignment.Center,
            ) {
                Mono("⚙", style = BackbeeType.label, color = backbeeColors.textMuted)
            }
        }
    }
}
