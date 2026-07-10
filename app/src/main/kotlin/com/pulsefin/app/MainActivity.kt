package com.pulsefin.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.root.PulseFinRoot
import com.pulsefin.core.designsystem.theme.PulseFinTheme
import com.pulsefin.core.playback.controller.PlaybackController
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    // No-op callback: if the user denies, playback still works — only the transport notification
    // is missing, which is the platform's normal (if degraded) behavior on a denial.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-first app: force transparent, light-content system bars regardless of the
        // device's light/dark setting, so the nav/status bars blend into the dark UI
        // instead of showing a light scrim.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            PulseFinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PulseFinRoot()
                }
                RequestNotificationPermissionOnFirstPlayback()
            }
        }
    }

    // Ask for POST_NOTIFICATIONS (required on API 33+) the first time something actually plays,
    // not on cold launch, so the prompt has an obvious reason attached to it.
    @Composable
    private fun RequestNotificationPermissionOnFirstPlayback(
        playbackController: PlaybackController = koinInject(),
    ) {
        val state by playbackController.state.collectAsStateWithLifecycle()
        LaunchedEffect(state.hasItem) {
            if (!state.hasItem) return@LaunchedEffect
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
