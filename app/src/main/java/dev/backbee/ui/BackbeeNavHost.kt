package dev.backbee.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import dev.backbee.ui.now.NowScreen
import dev.backbee.ui.now.NowViewModel
import dev.backbee.ui.settings.SettingsScreen
import dev.backbee.ui.settings.SettingsViewModel
import dev.backbee.ui.shelf.ShelfScreen
import dev.backbee.ui.shelf.ShelfViewModel

private object Routes {
    const val NOW = "now"
    const val ARCHIVE = "archive"
    const val EPISODE = "episode/{episodeId}"
    const val SHELF = "shelf"
    const val SETTINGS = "settings"
    const val COMPLETION = "completion/{showId}"

    fun episode(id: Long) = "episode/$id"
    fun completion(showId: Long) = "completion/$showId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackbeeNavHost(
    container: AppContainer,
    player: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.NOW,
        modifier = modifier,
    ) {
        composable(Routes.NOW) {
            val viewModel: NowViewModel = viewModel(
                factory = viewModelFactory { NowViewModel(container, player) }
            )
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("backbee") },
                        actions = {
                            IconButton(onClick = { navController.navigate(Routes.SHELF) }) {
                                Icon(Icons.Filled.LibraryBooks, contentDescription = "Shelf")
                            }
                            IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        },
                    )
                },
            ) { padding ->
                NowScreen(
                    viewModel = viewModel,
                    player = player,
                    onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                    onOpenShelf = { navController.navigate(Routes.SHELF) },
                    onOpenCompletion = { showId -> navController.navigate(Routes.completion(showId)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }

        composable(Routes.ARCHIVE) {
            val viewModel: ArchiveViewModel = viewModel(
                factory = viewModelFactory { ArchiveViewModel(container) }
            )
            ArchiveScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenEpisode = { id -> navController.navigate(Routes.episode(id)) },
            )
        }

        composable(
            route = Routes.EPISODE,
            arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
        ) { entry ->
            val episodeId = entry.arguments?.getLong("episodeId") ?: return@composable
            val viewModel: EpisodeDetailViewModel = viewModel(
                factory = viewModelFactory { EpisodeDetailViewModel(container, episodeId) }
            )
            EpisodeDetailScreen(
                viewModel = viewModel,
                player = player,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SHELF) {
            val viewModel: ShelfViewModel = viewModel(
                factory = viewModelFactory { ShelfViewModel(container) }
            )
            ShelfScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenCompletion = { showId -> navController.navigate(Routes.completion(showId)) },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { SettingsViewModel(container) }
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.COMPLETION,
            arguments = listOf(navArgument("showId") { type = NavType.LongType }),
        ) { entry ->
            val showId = entry.arguments?.getLong("showId") ?: return@composable
            val viewModel: CompletionViewModel = viewModel(
                factory = viewModelFactory { CompletionViewModel(container, showId) }
            )
            CompletionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onActivatedNextShow = {
                    navController.popBackStack(Routes.NOW, inclusive = false)
                },
            )
        }
    }
}
