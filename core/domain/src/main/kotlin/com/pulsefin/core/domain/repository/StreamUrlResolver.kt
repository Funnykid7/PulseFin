package com.pulsefin.core.domain.repository

/**
 * Resolves an authenticated, direct-play stream URL for a song by ID. Exists so
 * :core:playback can build download requests without depending on :core:data.
 */
interface StreamUrlResolver {
    suspend fun resolveStreamUrl(songId: String): String?
}
