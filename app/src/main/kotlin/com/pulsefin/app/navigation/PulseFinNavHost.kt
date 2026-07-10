package com.pulsefin.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.dp
import com.pulsefin.app.ui.components.LocalNavAnimatedContentScope
import com.pulsefin.app.ui.components.LocalSharedTransitionScope
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.designsystem.theme.searchShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulsefin.app.ui.home.HomeScreen
import com.pulsefin.app.ui.home.YourMixScreen
import com.pulsefin.app.ui.library.AlbumDetailScreen
import com.pulsefin.app.ui.library.AlbumsScreen
import com.pulsefin.app.ui.library.ArtistDetailScreen
import com.pulsefin.app.ui.library.ArtistsScreen
import com.pulsefin.app.ui.player.LyricsScreen
import com.pulsefin.app.ui.player.MiniPlayer
import com.pulsefin.app.ui.player.NowPlayingScreen
import com.pulsefin.app.ui.player.QueueScreen
import com.pulsefin.app.ui.playlist.PlaylistDetailScreen
import com.pulsefin.app.ui.playlist.PlaylistsScreen
import com.pulsefin.app.ui.search.SearchScreen
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.app.ui.theme.LocalArtworkColorScheme
import com.pulsefin.app.ui.theme.rememberArtworkColorScheme
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object Routes {
    const val HOME = "home"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val ALBUM_DETAIL = "album/{albumId}?art={art}"
    const val ARTIST_DETAIL = "artist/{artistId}"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val NOWPLAYING = "nowplaying"
    const val QUEUE = "queue"
    const val LYRICS = "lyrics"
    const val SEARCH = "search"

    // The art URL rides along so the detail hero (and its shared-element morph + Monet theme)
    // can render immediately instead of waiting for the track list to load from the server.
    fun albumDetail(id: String, artUrl: String?) = "album/$id?art=${Uri.encode(artUrl.orEmpty())}"
    fun artistDetail(id: String) = "artist/$id"
    fun playlistDetail(id: String) = "playlist/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SONGS, "Songs", Icons.Filled.LibraryMusic),
    Tab(Routes.ALBUMS, "Albums", Icons.Filled.Album),
    Tab(Routes.ARTISTS, "Artists", Icons.Filled.Person),
    Tab(Routes.PLAYLISTS, "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PulseFinNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authRepository = koinInject<AuthRepository>()
    val playbackController = koinInject<PlaybackController>()
    val mediaRepository = koinInject<MediaRepository>()
    val scope = rememberCoroutineScope()

    // Refresh the library whenever the app returns to the foreground so newly-added songs appear
    // without a manual pull. Skip the first resume — the tab ViewModels already sync on startup.
    // In-app navigation doesn't trigger Activity ON_RESUME, so this fires only on real returns.
    var skipFirstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (skipFirstResume) {
            skipFirstResume = false
        } else {
            scope.launch { mediaRepository.refreshLibrary(force = true) }
        }
    }
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    // LargeTopAppBar is designed to pair with exitUntilCollapsed (not enterAlways), which
    // makes the large title collapse smoothly instead of jumping around.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Surface sync failures once, centrally, instead of every screen swallowing its own
    // refreshLibrary() result — Room keeps serving the last-synced library regardless.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(mediaRepository) {
        mediaRepository.observeLastSyncError().collect { error ->
            if (error != null) {
                snackbarHostState.showSnackbar("Couldn't sync library — showing your last saved copy.")
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = tabs.firstOrNull { it.route == currentRoute }
    val isTab = currentTab != null
    val isFullScreen = currentRoute == Routes.NOWPLAYING || currentRoute == Routes.QUEUE ||
        currentRoute == Routes.LYRICS

    // Extract the current track's Monet scheme once, here, so the mini-player and the now-playing
    // screen share it. Hoisting keeps it alive across the mini-player's mount/unmount and across
    // the expand/collapse transition, so the palette never re-extracts and snaps mid-animation.
    val artworkScheme = rememberArtworkColorScheme(playbackState.artworkUrl)

    CompositionLocalProvider(LocalArtworkColorScheme provides artworkScheme) {
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        val signOutInteraction = remember { MutableInteractionSource() }
                        FilledTonalButton(
                            onClick = { scope.launch { authRepository.logout() } },
                            modifier = Modifier.pressScale(signOutInteraction, pressedScale = 0.92f),
                            shape = CircleShape,
                            interactionSource = signOutInteraction,
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
                    ArtworkTheme {
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
                            val tabInteraction = remember { MutableInteractionSource() }
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
                                modifier = Modifier.pressScale(tabInteraction, pressedScale = 0.9f),
                                interactionSource = tabInteraction,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME, enterTransition = tabEnter, exitTransition = tabExit) {
                        YourMixScreen(
                            contentPadding = innerPadding,
                            currentMediaId = playbackState.currentMediaId,
                            onAlbumClick = { id, art -> navController.navigate(Routes.albumDetail(id, art)) },
                        )
                    }
                    composable(Routes.SONGS, enterTransition = tabEnter, exitTransition = tabExit) {
                        HomeScreen(contentPadding = innerPadding, currentMediaId = playbackState.currentMediaId)
                    }
                    composable(Routes.ALBUMS, enterTransition = tabEnter, exitTransition = tabExit) {
                        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                            AlbumsScreen(
                                contentPadding = innerPadding,
                                onAlbumClick = { id, art -> navController.navigate(Routes.albumDetail(id, art)) },
                            )
                        }
                    }
                    composable(Routes.ARTISTS, enterTransition = tabEnter, exitTransition = tabExit) {
                        ArtistsScreen(
                            contentPadding = innerPadding,
                            onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                        )
                    }
                    composable(Routes.PLAYLISTS, enterTransition = tabEnter, exitTransition = tabExit) {
                        PlaylistsScreen(
                            contentPadding = innerPadding,
                            onPlaylistClick = { navController.navigate(Routes.playlistDetail(it)) },
                        )
                    }
                    composable(
                        route = Routes.ALBUM_DETAIL,
                        arguments = listOf(
                            navArgument("albumId") { type = NavType.StringType },
                            navArgument("art") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                        // Scale + fade: the shared-element art morph carries the spatial story.
                        enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                        popExitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
                    ) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                            AlbumDetailScreen(
                                albumId = entry.arguments?.getString("albumId").orEmpty(),
                                initialArtUrl = entry.arguments?.getString("art")?.ifBlank { null },
                                contentPadding = innerPadding,
                                currentMediaId = playbackState.currentMediaId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(
                        route = Routes.ARTIST_DETAIL,
                        arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
                    ) { entry ->
                        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                            ArtistDetailScreen(
                                artistId = entry.arguments?.getString("artistId").orEmpty(),
                                contentPadding = innerPadding,
                                onAlbumClick = { id, art -> navController.navigate(Routes.albumDetail(id, art)) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(
                        route = Routes.PLAYLIST_DETAIL,
                        arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
                    ) { entry ->
                        PlaylistDetailScreen(
                            playlistId = entry.arguments?.getString("playlistId").orEmpty(),
                            contentPadding = innerPadding,
                            currentMediaId = playbackState.currentMediaId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        Routes.NOWPLAYING,
                        enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                        popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                    ) {
                        NowPlayingScreen(
                            onCollapse = { navController.popBackStack() },
                            onOpenQueue = { navController.navigate(Routes.QUEUE) },
                            onOpenLyrics = { navController.navigate(Routes.LYRICS) },
                        )
                    }
                    composable(
                        Routes.LYRICS,
                        enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                        popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                    ) {
                        LyricsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        Routes.QUEUE,
                        enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                        popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() },
                    ) {
                        QueueScreen(
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
                            onAlbumClick = { id, art -> navController.navigate(Routes.albumDetail(id, art)) },
                            onArtistClick = { navController.navigate(Routes.artistDetail(it)) },
                        )
                    }
                }
            }
        }
    }
    }
}

// Tab switches use a shared fade-through so Songs/Albums/Artists glide instead of hard-cutting.
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn() + scaleIn(initialScale = 0.96f) }
private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut() }
