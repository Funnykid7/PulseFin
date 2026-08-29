package com.pulsefin.core.domain.repository

/**
 * Resolves an authenticated, direct-play stream URL for a song by ID. Exists so
 * :core:playback can build download requests without depending on :core:data.
 */
interface StreamUrlResolver {
    suspend fun resolveStreamUrl(songId: String): String?

    /**
     * Token-free direct-play URL for [songId], for persisting (e.g. in a Media3 DownloadRequest)
     * without baking the current session's auth token into on-disk state. Callers that need to
     * actually fetch bytes must attach a token first: [attachAuthToken] for non-suspend call
     * sites, or [resolveStreamUrl] for a suspend call site that wants a ready-to-play URL directly.
     */
    suspend fun resolveBaseStreamUrl(songId: String): String?

    /**
     * Attaches the current session's auth token to an otherwise-unauthenticated artwork URL.
     * Artwork URLs are stored (Room, queue-state persistence) without a token so it's never
     * written to disk; callers that need to actually fetch the image resolve it fresh here.
     */
    suspend fun resolveArtworkUrl(baseUrl: String): String?

    /**
     * Synchronous "attach the current token" step, for callers that can't suspend — currently
     * only Media3's ResolvingDataSource.Resolver, invoked on a download loader thread. Returns
     * [baseUrl] unchanged if there's no signed-in session yet.
     */
    fun attachAuthToken(baseUrl: String): String
}
