package com.pulsefin.core.domain.repository

import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the music library. Implementations mirror the Jellyfin API into Room
 * (single source of truth) and expose it as reactive [Flow]s for instant UI response.
 */
interface MediaRepository {
    fun albums(): Flow<List<Album>>
    fun artists(): Flow<List<Artist>>

    /** A flat list of songs from the server, for basic browse-and-play. */
    suspend fun songs(limit: Int = 200): PulseResult<List<Song>>

    suspend fun search(query: String): PulseResult<SearchResults>

    suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>>
}

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)
