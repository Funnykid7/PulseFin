package com.pulsefin.core.domain.repository

import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.Lyrics
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

    /** IDs of the songs the user has favorited, observed from Room for instant, offline-aware UI. */
    fun observeFavoriteIds(): Flow<Set<String>>

    /**
     * Fetches songs/albums/artists from the server and mirrors them into Room. Skips the network
     * if already synced this process unless [force] is set (e.g. pull-to-refresh).
     */
    suspend fun refreshLibrary(force: Boolean = false): PulseResult<Unit>

    suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>>

    suspend fun albumsForArtist(artistId: String): PulseResult<List<Album>>

    suspend fun search(query: String): PulseResult<SearchResults>

    /** Favorites the song on the server, then mirrors the state into Room optimistically. */
    suspend fun setFavorite(songId: String, favorite: Boolean): PulseResult<Unit>

    /** Most recently added albums, newest first (for the Home feed). Network. */
    suspend fun recentlyAdded(limit: Int): PulseResult<List<Album>>

    /** Timed or plain lyrics for a song; a [Failure] or empty result means none are available. */
    suspend fun lyrics(songId: String): PulseResult<Lyrics>

    /** Past search queries, newest first, for quick re-run from the Search screen. */
    fun observeRecentSearches(limit: Int): Flow<List<String>>

    /** Records a completed search query, bumping it to the top if already present. */
    suspend fun recordSearch(query: String)

    suspend fun removeRecentSearch(query: String)

    suspend fun clearRecentSearches()
}

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)
