package com.pulsefin.app.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulsefin.app.ui.home.HomeScreen
import com.pulsefin.app.ui.player.MiniPlayer
import com.pulsefin.app.ui.player.NowPlayingScreen
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Top-level navigation graph. Destinations are added as feature screens land. */
object Routes {
    const val HOME = "home"
    const val NOWPLAYING = "nowplaying"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseFinNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authRepository = koinInject<AuthRepository>()
    val playbackController = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentRoute == Routes.HOME) {
                LargeTopAppBar(
                    title = { Text("PulseFin", color = MaterialTheme.colorScheme.primary) },
                    actions = {
                        TextButton(onClick = { scope.launch { authRepository.logout() } }) {
                            Text("Sign out")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (currentRoute != Routes.NOWPLAYING) {
                MiniPlayer(
                    state = playbackState,
                    onTogglePlayPause = playbackController::togglePlayPause,
                    onClick = { navController.navigate(Routes.NOWPLAYING) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    contentPadding = innerPadding,
                    currentMediaId = playbackState.currentMediaId,
                )
            }
            composable(Routes.NOWPLAYING) {
                NowPlayingScreen(
                    contentPadding = innerPadding,
                    onCollapse = { navController.popBackStack() },
                )
            }
        }
    }
}
