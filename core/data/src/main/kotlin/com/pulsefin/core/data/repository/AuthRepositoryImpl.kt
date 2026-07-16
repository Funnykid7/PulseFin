package com.pulsefin.core.data.repository

import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.data.local.PulseFinDatabase
import com.pulsefin.core.data.local.Session
import com.pulsefin.core.data.local.SessionStore
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.domain.repository.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName

/**
 * Authenticates against the configured Jellyfin server using the official Kotlin SDK and
 * persists the resulting session. [authState] is derived directly from the persisted
 * session, so login/logout drive navigation reactively (set-and-forget).
 */
class AuthRepositoryImpl(
    private val jellyfin: Jellyfin,
    private val sessionStore: SessionStore,
    private val dispatchers: AppDispatchers,
    private val database: PulseFinDatabase,
) : AuthRepository {

    override val authState: Flow<AuthState> = sessionStore.session.map { session ->
        if (session != null) {
            AuthState.LoggedIn(session.serverUrl, session.userName)
        } else {
            AuthState.LoggedOut
        }
    }

    override suspend fun loginWithPassword(
        serverUrl: String,
        username: String,
        password: String,
    ): PulseResult<AuthState.LoggedIn> = withContext(dispatchers.io) {
        PulseResult.runCatchingResult {
            val baseUrl = normalizeServerUrl(serverUrl)
            val api = jellyfin.createApi(baseUrl = baseUrl)
            val result = api.userApi.authenticateUserByName(
                data = AuthenticateUserByName(username = username, pw = password),
            ).content
            val token = requireNotNull(result.accessToken) {
                "Server did not return an access token"
            }
            val resolvedName = result.user?.name ?: username
            sessionStore.save(
                Session(
                    serverUrl = baseUrl,
                    accessToken = token,
                    userName = resolvedName,
                    userId = result.user?.id?.toString(),
                ),
            )
            AuthState.LoggedIn(baseUrl, resolvedName)
        }
    }

    override suspend fun loginWithApiKey(
        serverUrl: String,
        apiKey: String,
    ): PulseResult<AuthState.LoggedIn> = withContext(dispatchers.io) {
        PulseResult.runCatchingResult {
            val baseUrl = normalizeServerUrl(serverUrl)
            val api = jellyfin.createApi(baseUrl = baseUrl, accessToken = apiKey)
            // An API key is just a token — validate it against the server before saving a
            // session, otherwise a typo'd URL/key would silently "succeed" with no userId,
            // crashing later on any call that requires one (e.g. playlist creation).
            val user = api.userApi.getCurrentUser().content
            val resolvedName = user.name ?: "API Key"
            sessionStore.save(
                Session(
                    serverUrl = baseUrl,
                    accessToken = apiKey,
                    userName = resolvedName,
                    userId = user.id.toString(),
                ),
            )
            AuthState.LoggedIn(baseUrl, resolvedName)
        }
    }

    override suspend fun logout() {
        sessionStore.clear()
        database.clearAll()
    }
}

/** Accepts bare host:port (as users typically type) and normalizes to a full base URL. */
private fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        // Default to https: most self-hosted Jellyfin deployments sit behind a TLS-terminating
        // reverse proxy. Users on a plain-HTTP LAN server can still type "http://" explicitly.
        "https://$trimmed"
    }
}
