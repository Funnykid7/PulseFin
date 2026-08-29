package com.pulsefin.core.data.jellyfin

import com.pulsefin.core.data.local.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException

/**
 * Builds and caches an authenticated [ApiClient] for the current session. Returns null when
 * logged out. The rest of the data layer goes through this so exactly one place knows how to
 * turn a persisted session into a live, token-bearing Jellyfin client.
 */
class JellyfinApiProvider(
    private val jellyfin: Jellyfin,
    private val sessionStore: SessionStore,
) {
    private val mutex = Mutex()

    private var cachedForToken: String? = null
    private var cachedApi: ApiClient? = null

    /** The signed-in user's ID, needed by Jellyfin calls that require an explicit owner (e.g. playlist creation). */
    suspend fun currentUserId(): String? = sessionStore.session.first()?.userId

    /**
     * Synchronous, cheap token read — no [ApiClient] build, no disk I/O, no mutex. For callers
     * that can't suspend, e.g. Media3's ResolvingDataSource.Resolver.
     */
    val currentAccessToken: String? get() = sessionStore.currentSession?.accessToken

    /**
     * Clears the session if [error] is a 401 (server-side session revocation — password change,
     * revoked API key, token-expiry policy). Every repository call site that goes through
     * [PulseResult.runCatchingResult][com.pulsefin.core.common.result.PulseResult.Companion.runCatchingResult]
     * should route its failure through this so a revoked session actually transitions AuthState
     * to LoggedOut instead of leaving the UI permanently stuck reporting generic failures.
     */
    suspend fun invalidateSessionIfUnauthorized(error: Throwable) {
        if ((error as? InvalidStatusException)?.status == 401) {
            sessionStore.clear()
        }
    }

    /** The authenticated client for the active session, or null if not signed in. */
    suspend fun api(): ApiClient? {
        val session = sessionStore.session.first() ?: return null
        return mutex.withLock {
            val existing = cachedApi
            if (existing != null && cachedForToken == session.accessToken) {
                existing
            } else {
                jellyfin.createApi(
                    baseUrl = session.serverUrl,
                    accessToken = session.accessToken,
                ).also {
                    cachedApi = it
                    cachedForToken = session.accessToken
                }
            }
        }
    }
}
