package com.pulsefin.app.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.navigation.PulseFinNavHost
import com.pulsefin.app.ui.login.LoginScreen
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.domain.repository.AuthState
import org.koin.compose.koinInject

/**
 * Auth gate: switches between the login screen and the main navigation graph based on the
 * persisted session, so the app opens straight into the library once signed in.
 */
@Composable
fun PulseFinRoot(modifier: Modifier = Modifier) {
    val authRepository = koinInject<AuthRepository>()
    val authState by authRepository.authState.collectAsStateWithLifecycle(
        initialValue = AuthState.Unknown,
    )

    when (authState) {
        AuthState.Unknown -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        AuthState.LoggedOut -> LoginScreen()

        is AuthState.LoggedIn -> PulseFinNavHost(modifier = modifier)
    }
}
