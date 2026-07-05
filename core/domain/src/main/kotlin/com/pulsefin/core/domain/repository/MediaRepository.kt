package com.pulsefin.core.domain.repository

import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the music library. The browse lists are served from Room (single source of
 * truth) as reactive [Flow]s for instant, offline-capable reads; [refreshLibrary] syncs them
 * from the Jellyfin server. Detail/search queries go directly to the network.
 */
interface MediaRepository {
    fun observeSongs(): Flow<List<Song>>
    fun observeAlbums(): Flow<List<Album>>
    fun observeArtists(): Flow<List<Artist>>

    /**
     * Fetches songs/albums/artists from the server and mirrors them into Room. Skips the network
     * if already synced this process unless [force] is set (e.g. pull-to-refresh).
     */
    suspend fun refreshLibrary(force: Boolean = false): PulseResult<Unit>

    suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>>

    suspend fun albumsForArtist(artistId: String): PulseResult<List<Album>>

    suspend fun search(query: String): PulseResult<SearchResults>
}

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)
