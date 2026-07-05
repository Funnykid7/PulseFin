package com.pulsefin.core.domain.repository

import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.Song

/**
 * Read access to the music library, fetched from the Jellyfin server (Room mirroring for
 * offline is a later increment).
 */
interface MediaRepository {
    /** A flat list of songs from the server, for basic browse-and-play. */
    suspend fun songs(limit: Int = 200): PulseResult<List<Song>>

    suspend fun albums(): PulseResult<List<Album>>

    suspend fun artists(): PulseResult<List<Artist>>

    suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>>

    suspend fun albumsForArtist(artistId: String): PulseResult<List<Album>>

    suspend fun search(query: String): PulseResult<SearchResults>
}

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)
