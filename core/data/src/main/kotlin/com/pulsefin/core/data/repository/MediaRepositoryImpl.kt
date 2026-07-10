package com.pulsefin.core.data.repository

import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.data.jellyfin.JellyfinApiProvider
import com.pulsefin.core.data.local.AlbumDao
import com.pulsefin.core.data.local.AlbumEntity
import com.pulsefin.core.data.local.ArtistDao
import com.pulsefin.core.data.local.ArtistEntity
import com.pulsefin.core.data.local.RecentSearchDao
import com.pulsefin.core.data.local.RecentSearchEntity
import com.pulsefin.core.data.local.SongDao
import com.pulsefin.core.data.local.SongEntity
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.LyricLine
import com.pulsefin.core.domain.model.Lyrics
import com.pulsefin.core.domain.model.MediaId
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.domain.repository.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/**
 * Room is the single source of truth for the browse lists: the UI observes the DAOs, while
 * [refreshLibrary] mirrors the Jellyfin server into Room. If a refresh fails (e.g. offline),
 * the observed Room data simply stays, so the last-synced library remains browsable.
 * Detail and search queries hit the network directly.
 */
class MediaRepositoryImpl(
    private val apiProvider: JellyfinApiProvider,
    private val dispatchers: AppDispatchers,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val recentSearchDao: RecentSearchDao,
) : MediaRepository {

    private val refreshMutex = Mutex()

    @Volatile
    private var hasSyncedThisProcess = false

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAll().map { rows -> rows.map { it.toSong() } }

    override fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAll().map { rows -> rows.map { it.toAlbum() } }

    override fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { rows -> rows.map { it.toArtist() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        songDao.observeFavoriteIds().map { it.toSet() }

    override suspend fun refreshLibrary(force: Boolean): PulseResult<Unit> = withContext(dispatchers.io) {
        PulseResult.runCatchingResult {
            refreshMutex.withLock {
                // Dedupe the auto-sync the three tabs each kick off; pull-to-refresh forces it.
                if (hasSyncedThisProcess && !force) return@withLock
                val api = requireApi()
                val songs = fetchItems(api, BaseItemKind.AUDIO, limit = 500).map { it.toSong(api) }
                val albums = fetchItems(api, BaseItemKind.MUSIC_ALBUM).map { it.toAlbum(api) }
                val artists = fetchItems(api, BaseItemKind.MUSIC_ARTIST).map { it.toArtist(api) }
                // Atomic swaps -> observers see a single emission, no empty flash.
                songDao.replaceAll(songs.map { it.toEntity() })
                albumDao.replaceAll(albums.map { it.toEntity() })
                artistDao.replaceAll(artists.map { it.toEntity() })
                hasSyncedThisProcess = true
            }
        }
    }

    override suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.itemsApi.getItems(
                    GetItemsRequest(
                        parentId = UUID.fromString(albumId),
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    ),
                ).content.items.orEmpty().map { it.toSong(api) }
            }
        }

    override suspend fun albumsForArtist(artistId: String): PulseResult<List<Album>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.itemsApi.getItems(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        albumArtistIds = listOf(UUID.fromString(artistId)),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    ),
                ).content.items.orEmpty().map { it.toAlbum(api) }
            }
        }

    override suspend fun search(query: String): PulseResult<SearchResults> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                if (query.isBlank()) {
                    return@runCatchingResult SearchResults(emptyList(), emptyList(), emptyList())
                }
                val api = requireApi()
                val items = api.itemsApi.getItems(
                    GetItemsRequest(
                        searchTerm = query,
                        includeItemTypes = listOf(
                            BaseItemKind.AUDIO,
                            BaseItemKind.MUSIC_ALBUM,
                            BaseItemKind.MUSIC_ARTIST,
                        ),
                        recursive = true,
                        limit = 60,
                    ),
                ).content.items.orEmpty()
                SearchResults(
                    artists = items.filter { it.type == BaseItemKind.MUSIC_ARTIST }.map { it.toArtist(api) },
                    albums = items.filter { it.type == BaseItemKind.MUSIC_ALBUM }.map { it.toAlbum(api) },
                    songs = items.filter { it.type == BaseItemKind.AUDIO }.map { it.toSong(api) },
                )
            }
        }

    override suspend fun setFavorite(songId: String, favorite: Boolean): PulseResult<Unit> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                // Optimistic: update Room first so the heart flips instantly, then tell the server.
                songDao.setFavorite(songId, favorite)
                val api = requireApi()
                val itemId = UUID.fromString(songId)
                if (favorite) api.userLibraryApi.markFavoriteItem(itemId = itemId)
                else api.userLibraryApi.unmarkFavoriteItem(itemId = itemId)
                Unit
            }
        }

    override suspend fun recentlyAdded(limit: Int): PulseResult<List<Album>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.itemsApi.getItems(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = limit,
                    ),
                ).content.items.orEmpty().map { it.toAlbum(api) }
            }
        }

    override suspend fun lyrics(songId: String): PulseResult<Lyrics> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                val dto = api.lyricsApi.getLyrics(itemId = UUID.fromString(songId)).content
                Lyrics(
                    dto.lyrics.map { line ->
                        // Jellyfin start times are in ticks (100-ns units) -> milliseconds.
                        LyricLine(startMs = line.start?.let { it / 10_000 }, text = line.text)
                    },
                )
            }
        }

    override fun observeRecentSearches(limit: Int): Flow<List<String>> =
        recentSearchDao.observeRecent(limit).map { rows -> rows.map { it.query } }

    override suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        withContext(dispatchers.io) {
            recentSearchDao.upsert(RecentSearchEntity(trimmed, System.currentTimeMillis()))
        }
    }

    override suspend fun removeRecentSearch(query: String) = withContext(dispatchers.io) {
        recentSearchDao.delete(query)
    }

    override suspend fun clearRecentSearches() = withContext(dispatchers.io) {
        recentSearchDao.clearAll()
    }

    private suspend fun requireApi(): ApiClient = apiProvider.api() ?: error("Not signed in")

    private suspend fun fetchItems(api: ApiClient, type: BaseItemKind, limit: Int? = null): List<BaseItemDto> =
        api.itemsApi.getItems(
            GetItemsRequest(
                includeItemTypes = listOf(type),
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                limit = limit,
            ),
        ).content.items.orEmpty()
}

// --- Jellyfin DTO -> domain ---

private fun BaseItemDto.toSong(api: ApiClient): Song = Song(
    id = MediaId(id.toString()),
    title = name ?: "Unknown",
    albumName = album.orEmpty(),
    artistName = artists?.joinToString(", ")?.ifBlank { null } ?: albumArtist ?: "Unknown artist",
    durationMs = (runTimeTicks ?: 0L) / 10_000,
    artworkUrl = artworkUrl(api),
    streamUrl = ensureApiKey(api.audioApi.getAudioStreamUrl(itemId = id, static = true), api.accessToken),
    isFavorite = userData?.isFavorite ?: false,
)

private fun BaseItemDto.toAlbum(api: ApiClient): Album = Album(
    id = MediaId(id.toString()),
    name = name ?: "Unknown album",
    artistName = albumArtist ?: artists?.joinToString(", ") ?: "Unknown artist",
    artworkUrl = artworkUrl(api),
    year = productionYear,
)

private fun BaseItemDto.toArtist(api: ApiClient): Artist = Artist(
    id = MediaId(id.toString()),
    name = name ?: "Unknown artist",
    artworkUrl = artworkUrl(api),
)

private fun BaseItemDto.artworkUrl(api: ApiClient): String? = runCatching {
    // Store the base URL; callers append the size they need via sizedArtUrl (tiny for list
    // thumbnails, larger for the Now Playing hero) so lists stay cheap to scroll.
    api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY)
}.getOrNull()

/** Direct-play URLs must carry auth for ExoPlayer; append the token if the SDK didn't. */
private fun ensureApiKey(url: String, token: String?): String {
    if (token.isNullOrBlank() || url.contains("api_key=", ignoreCase = true)) return url
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}api_key=$token"
}

// --- domain <-> Room entity ---

private fun Song.toEntity() = SongEntity(id.value, title, albumName, artistName, durationMs, artworkUrl, streamUrl, isFavorite)
private fun Album.toEntity() = AlbumEntity(id.value, name, artistName, artworkUrl, year)
private fun Artist.toEntity() = ArtistEntity(id.value, name, artworkUrl)

private fun SongEntity.toSong() = Song(MediaId(id), title, albumName, artistName, durationMs, artworkUrl, streamUrl, isFavorite)
private fun AlbumEntity.toAlbum() = Album(MediaId(id), name, artistName, artworkUrl, year)
private fun ArtistEntity.toArtist() = Artist(MediaId(id), name, artworkUrl)
