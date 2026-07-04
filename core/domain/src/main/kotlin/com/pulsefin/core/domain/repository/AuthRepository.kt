package com.pulsefin.core.domain.repository

import com.pulsefin.core.common.result.PulseResult
import kotlinx.coroutines.flow.Flow

/** Set-and-forget authentication state for the single configured Jellyfin server. */
sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val serverUrl: String, val userName: String) : AuthState
}

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun loginWithPassword(
        serverUrl: String,
        username: String,
        password: String,
    ): PulseResult<AuthState.LoggedIn>

    suspend fun loginWithApiKey(
        serverUrl: String,
        apiKey: String,
    ): PulseResult<AuthState.LoggedIn>

    suspend fun logout()
}
