package com.pulsefin.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.dp
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.designsystem.theme.searchShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulsefin.app.ui.home.HomeScreen
import com.pulsefin.app.ui.library.AlbumDetailScreen
import com.pulsefin.app.ui.library.AlbumsScreen
import com.pulsefin.app.ui.library.ArtistDetailScreen
import com.pulsefin.app.ui.library.ArtistsScreen
import com.pulsefin.app.ui.player.MiniPlayer
import com.pulsefin.app.ui.player.NowPlayingScreen
import com.pulsefin.app.ui.player.QueueScreen
import com.pulsefin.app.ui.search.SearchScreen
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object Routes {
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val ARTIST_DETAIL = "artist/{artistId}"
    const val NOWPLAYING = "nowplaying"
    const val QUEUE = "queue"
    const val SEARCH = "search"

    fun albumDetail(id: String) = "album/$id"
    fun artistDetail(id: String) = "artist/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.SONGS, "Songs", Icons.Filled.LibraryMusic),
    Tab(Routes.ALBUMS, "Albums", Icons.Filled.Album),
    Tab(Routes.ARTISTS, "Artists", Icons.Filled.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseFinNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authRepository = koinInject<AuthRepository>()
    val playbackController = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    // LargeTopAppBar is designed to pair with exitUntilCollapsed (not enterAlways), which
    // makes the large title collapse smoothly instead of jumping around.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = tabs.firstOrNull { it.route == currentRoute }
    val isTab = currentTab != null
    val isFullScreen = currentRoute == Routes.NOWPLAYING || currentRoute == Routes.QUEUE

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isTab) {
                LargeTopAppBar(
                    title = {
                        Text(currentTab?.label ?: "PulseFin", color = MaterialTheme.colorScheme.primary)
                    },
                    actions = {
                        val searchInteraction = remember { MutableInteractionSource() }
                        FilledTonalIconButton(
                            onClick = { navController.navigate(Routes.SEARCH) },
                            modifier = Modifier.pressScale(searchInteraction),
                            shape = searchShape(),
                            interactionSource = searchInteraction,
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { scope.launch { authRepository.logout() } },
                            shape = CircleShape,
                        ) {
                            Text("Sign out")
                        }
                        Spacer(Modifier.width(8.dp))
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            // Mini-player stacks above the tab bar. The NavigationBar consumes the system
            // inset on tab routes; on detail routes there's no NavigationBar, so pad the column.
            Column(modifier = if (isTab) Modifier else Modifier.navigationBarsPadding()) {
                AnimatedVisibility(
                    visible = playbackState.hasItem && !isFullScreen,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    ArtworkTheme(playbackState.artworkUrl) {
                        MiniPlayer(
                            state = playbackState,
                            onTogglePlayPause = playbackController::togglePlayPause,
                            onClick = { navController.navigate(Routes.NOWPLAYING) },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = isTab,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Routes.SONGS) {
            composable(Routes.SONGS) {
                HomeScreen(contentPadding = innerPadding, currentMediaId = playbackState.currentMediaId)
            }
            composable(Routes.ALBUMS) {
                AlbumsScreen(
                    contentPadding = innerPadding,
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                )
            }
            composable(Routes.ARTISTS) {
                ArtistsScreen(
                    contentPadding = innerPadding,
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                )
            }
            composable(
                route = Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) { entry ->
                AlbumDetailScreen(
                    albumId = entry.arguments?.getString("albumId").orEmpty(),
                    contentPadding = innerPadding,
                    currentMediaId = playbackState.currentMediaId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ARTIST_DETAIL,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { entry ->
                ArtistDetailScreen(
                    artistId = entry.arguments?.getString("artistId").orEmpty(),
                    contentPadding = innerPadding,
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.NOWPLAYING,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
            ) {
                NowPlayingScreen(
                    contentPadding = innerPadding,
                    onCollapse = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                )
            }
            composable(
                Routes.QUEUE,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
            ) {
                QueueScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.SEARCH,
                enterTransition = {
                    fadeIn() + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(0.9f, 0f))
                },
                popExitTransition = {
                    fadeOut() + scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(0.9f, 0f))
                },
            ) {
                SearchScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Routes.albumDetail(it)) },
                    onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                )
            }
        }
    }
}
