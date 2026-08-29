package com.pulsefin.app.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.app.playback.PlaybackScrobbler
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.repository.AuthRepository
import kotlinx.coroutines.launch

/** Login failures, so [LoginScreen] can resolve display text via stringResource. */
sealed interface LoginError {
    data object Required : LoginError
    data object Failed : LoginError
}

data class LoginUiState(
    val serverScheme: String = "http",
    // Host[:port] only — no scheme. The scheme comes from serverScheme via the dropdown.
    val serverHost: String = "",
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val playbackScrobbler: PlaybackScrobbler,
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onServerSchemeChange(scheme: String) {
        uiState = uiState.copy(serverScheme = scheme, error = null)
    }

    fun onServerHostChange(value: String) {
        uiState = uiState.copy(serverHost = value, error = null)
    }

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun submit() {
        val current = uiState
        if (current.isSubmitting) return
        // Password isn't trimmed — whitespace there could be intentional and part of the secret.
        val username = current.username.trim()
        if (current.serverHost.isBlank() || username.isBlank()) {
            uiState = current.copy(error = LoginError.Required)
            return
        }
        uiState = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val result = authRepository.loginWithPassword(
                serverUrl = "${current.serverScheme}://${current.serverHost.trim()}",
                username = username,
                password = current.password,
            )
            // On success, the authState flow drives navigation; just clear the spinner.
            uiState = when (result) {
                is PulseResult.Success -> {
                    playbackScrobbler.resetSession()
                    uiState.copy(isSubmitting = false)
                }
                is PulseResult.Failure -> uiState.copy(
                    isSubmitting = false,
                    error = LoginError.Failed,
                )
            }
        }
    }
}
