package com.pulsefin.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsefin.app.ui.home.HomeScreen
import com.pulsefin.app.ui.player.MiniPlayer
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Top-level navigation graph. Destinations are added as feature screens land. */
object Routes {
    const val HOME = "home"
}

@Composable
fun PulseFinNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authRepository = koinInject<AuthRepository>()
    val playbackController = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()
    val playbackState by playbackController.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            MiniPlayer(
                state = playbackState,
                onTogglePlayPause = playbackController::togglePlayPause,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onLogout = { scope.launch { authRepository.logout() } },
                )
            }
        }
    }
}
