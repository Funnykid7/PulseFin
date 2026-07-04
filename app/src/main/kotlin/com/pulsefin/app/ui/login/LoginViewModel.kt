package com.pulsefin.app.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.repository.AuthRepository
import kotlinx.coroutines.launch

data class LoginUiState(
    // Prefilled with the configured test server for convenience; not a secret.
    val server: String = "http://100.103.47.77:8096",
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onServerChange(value: String) {
        uiState = uiState.copy(server = value, error = null)
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
        if (current.server.isBlank() || current.username.isBlank()) {
            uiState = current.copy(error = "Server and username are required")
            return
        }
        uiState = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val result = authRepository.loginWithPassword(
                serverUrl = current.server,
                username = current.username,
                password = current.password,
            )
            // On success, the authState flow drives navigation; just clear the spinner.
            uiState = when (result) {
                is PulseResult.Success -> uiState.copy(isSubmitting = false)
                is PulseResult.Failure -> uiState.copy(
                    isSubmitting = false,
                    error = result.error.message ?: "Login failed",
                )
            }
        }
    }
}
