package com.pulsefin.core.data.jellyfin

import com.pulsefin.core.data.local.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient

/**
 * Builds and caches an authenticated [ApiClient] for the current session. Returns null when
 * logged out. The rest of the data layer goes through this so exactly one place knows how to
 * turn a persisted session into a live, token-bearing Jellyfin client.
 */
class JellyfinApiProvider(
    private val jellyfinClientFactory: JellyfinClientFactory,
    private val sessionStore: SessionStore,
) {
    private val jellyfin = jellyfinClientFactory.create()
    private val mutex = Mutex()

    private var cachedForToken: String? = null
    private var cachedApi: ApiClient? = null

    /** The signed-in user's ID, needed by Jellyfin calls that require an explicit owner (e.g. playlist creation). */
    suspend fun currentUserId(): String? = sessionStore.session.first()?.userId

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
