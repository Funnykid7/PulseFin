package com.pulsefin.core.data.repository

import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.data.jellyfin.JellyfinApiProvider
import com.pulsefin.core.data.jellyfin.ensureApiKey
import com.pulsefin.core.data.local.AlbumDao
import com.pulsefin.core.data.local.AlbumEntity
import com.pulsefin.core.data.local.ArtistDao
import com.pulsefin.core.data.local.ArtistEntity
import com.pulsefin.core.data.local.PlaylistDao
import com.pulsefin.core.data.local.PlaylistEntity
import com.pulsefin.core.data.local.RecentSearchDao
import com.pulsefin.core.data.local.RecentSearchEntity
import com.pulsefin.core.data.local.SongDao
import com.pulsefin.core.data.local.SongEntity
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.LyricLine
import com.pulsefin.core.domain.model.Lyrics
import com.pulsefin.core.domain.model.MediaId
import com.pulsefin.core.domain.model.Playlist
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.domain.repository.SearchResults
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UpdatePlaylistDto
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
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
    private val playlistDao: PlaylistDao,
) : MediaRepository {

    private val refreshMutex = Mutex()

    @Volatile
    private var hasSyncedThisProcess = false

    private val _lastSyncError = MutableStateFlow<Throwable?>(null)
    override fun observeLastSyncError(): Flow<Throwable?> = _lastSyncError.asStateFlow()

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAll().map { rows -> rows.map { it.toSong() } }

    override fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAll().map { rows -> rows.map { it.toAlbum() } }

    override fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { rows -> rows.map { it.toArtist() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        songDao.observeFavoriteIds().map { it.toSet() }

    override suspend fun refreshLibrary(force: Boolean): PulseResult<Unit> = withContext(dispatchers.io) {
        val result = PulseResult.runCatchingResult {
            refreshMutex.withLock {
                // Dedupe the auto-sync the three tabs each kick off; pull-to-refresh forces it.
                if (hasSyncedThisProcess && !force) return@withLock
                val api = requireApi()
                coroutineScope {
                    val songsDeferred = async { fetchAllItems(api, BaseItemKind.AUDIO).map { it.toSong(api) } }
                    val albumsDeferred = async { fetchAllItems(api, BaseItemKind.MUSIC_ALBUM).map { it.toAlbum(api) } }
                    val artistsDeferred = async { fetchAllItems(api, BaseItemKind.MUSIC_ARTIST).map { it.toArtist(api) } }
                    val playlistsDeferred = async {
                        fetchAllItems(api, BaseItemKind.PLAYLIST).map { async { it.toPlaylist(api) } }.awaitAll()
                    }
                    // Atomic swaps -> observers see a single emission, no empty flash.
                    songDao.replaceAll(songsDeferred.await().map { it.toEntity() })
                    albumDao.replaceAll(albumsDeferred.await().map { it.toEntity() })
                    artistDao.replaceAll(artistsDeferred.await().map { it.toEntity() })
                    playlistDao.replaceAll(playlistsDeferred.await().map { it.toEntity() })
                }
                hasSyncedThisProcess = true
            }
        }
        _lastSyncError.value = (result as? PulseResult.Failure)?.error
        result
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
            // Optimistic: update Room first so the heart flips instantly, then tell the server.
            songDao.setFavorite(songId, favorite)
            val result = PulseResult.runCatchingResult {
                val api = requireApi()
                val itemId = UUID.fromString(songId)
                if (favorite) api.userLibraryApi.markFavoriteItem(itemId = itemId)
                else api.userLibraryApi.unmarkFavoriteItem(itemId = itemId)
                Unit
            }
            // Roll back the optimistic write on failure so Room doesn't keep lying to the user.
            if (result is PulseResult.Failure) songDao.setFavorite(songId, !favorite)
            result
        }

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll().map { rows -> rows.map { it.toPlaylist() } }

    override suspend fun createPlaylist(name: String, songIds: List<String>): PulseResult<Unit> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                val userId = UUID.fromString(requireUserId())
                val created = api.playlistsApi.createPlaylist(
                    CreatePlaylistDto(
                        name = name,
                        ids = songIds.map { UUID.fromString(it) },
                        userId = userId,
                        mediaType = MediaType.AUDIO,
                        users = emptyList(),
                        isPublic = false,
                    ),
                ).content
                upsertSinglePlaylist(api, created.id)
            }
        }

    override suspend fun renamePlaylist(playlistId: String, name: String): PulseResult<Unit> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.playlistsApi.updatePlaylist(UUID.fromString(playlistId), UpdatePlaylistDto(name = name))
                upsertSinglePlaylist(api, playlistId)
            }
        }

    override suspend fun deletePlaylist(playlistId: String): PulseResult<Unit> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                requireApi().libraryApi.deleteItem(UUID.fromString(playlistId))
                playlistDao.delete(playlistId)
            }
        }

    override suspend fun songsForPlaylist(playlistId: String): PulseResult<List<Song>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                fetchAllPlaylistItems(api, playlistId).map { item ->
                    item.toSong(api).copy(playlistItemId = item.playlistItemId)
                }
            }
        }

    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>): PulseResult<Unit> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                val userId = UUID.fromString(requireUserId())
                api.playlistsApi.addItemToPlaylist(
                    UUID.fromString(playlistId),
                    songIds.map { UUID.fromString(it) },
                    userId,
                )
                upsertSinglePlaylist(api, playlistId)
            }
        }

    override suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>): PulseResult<Unit> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.playlistsApi.removeItemFromPlaylist(playlistId, entryIds)
                upsertSinglePlaylist(api, playlistId)
            }
        }

    // Scrobbling is best-effort telemetry to the user's own Jellyfin server (play history/"now
    // playing" on other clients) — failures are swallowed rather than surfaced, since a dropped
    // ping shouldn't interrupt playback or show an error the user can't act on.
    override suspend fun reportPlaybackStart(songId: String, playSessionId: String) {
        withContext(dispatchers.io) {
            runCatching {
                requireApi().playStateApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        itemId = UUID.fromString(songId),
                        playMethod = PlayMethod.DIRECT_PLAY,
                        playSessionId = playSessionId,
                        canSeek = true,
                        isPaused = false,
                        isMuted = false,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    ),
                )
            }
        }
    }

    override suspend fun reportPlaybackProgress(
        songId: String,
        playSessionId: String,
        positionMs: Long,
        isPaused: Boolean,
    ) {
        withContext(dispatchers.io) {
            runCatching {
                requireApi().playStateApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = UUID.fromString(songId),
                        positionTicks = positionMs * 10_000,
                        playMethod = PlayMethod.DIRECT_PLAY,
                        playSessionId = playSessionId,
                        canSeek = true,
                        isPaused = isPaused,
                        isMuted = false,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    ),
                )
            }
        }
    }

    override suspend fun reportPlaybackStopped(songId: String, playSessionId: String, positionMs: Long) {
        withContext(dispatchers.io) {
            runCatching {
                requireApi().playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = UUID.fromString(songId),
                        positionTicks = positionMs * 10_000,
                        playSessionId = playSessionId,
                        failed = false,
                    ),
                )
            }
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

    private suspend fun requireUserId(): String = apiProvider.currentUserId() ?: error("Not signed in")

    /**
     * Re-fetches and upserts just the one edited playlist, instead of a full-library resync
     * (which would otherwise re-fetch every playlist, including a per-playlist art request, for
     * a single add/create/rename edit).
     */
    private suspend fun upsertSinglePlaylist(api: ApiClient, playlistId: String) {
        val item = api.itemsApi.getItems(
            GetItemsRequest(ids = listOf(UUID.fromString(playlistId))),
        ).content.items.orEmpty().firstOrNull() ?: return
        playlistDao.upsertAll(listOf(item.toPlaylist(api).toEntity()))
    }

    /** Pages through a playlist's items — same shape as [fetchAllItems] but via the playlist API. */
    private suspend fun fetchAllPlaylistItems(
        api: ApiClient,
        playlistId: String,
        pageSize: Int = 200,
    ): List<BaseItemDto> {
        val out = mutableListOf<BaseItemDto>()
        var startIndex = 0
        while (true) {
            val page = api.playlistsApi.getPlaylistItems(
                GetPlaylistItemsRequest(
                    playlistId = UUID.fromString(playlistId),
                    startIndex = startIndex,
                    limit = pageSize,
                ),
            ).content
            val items = page.items.orEmpty()
            if (items.isEmpty()) break
            out += items
            if (out.size >= page.totalRecordCount) break
            startIndex += pageSize
        }
        return out
    }

    /** Pages through the full library for [type] — a single request would silently truncate. */
    private suspend fun fetchAllItems(
        api: ApiClient,
        type: BaseItemKind,
        pageSize: Int = 200,
    ): List<BaseItemDto> {
        val out = mutableListOf<BaseItemDto>()
        var startIndex = 0
        while (true) {
            val page = api.itemsApi.getItems(
                GetItemsRequest(
                    includeItemTypes = listOf(type),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    startIndex = startIndex,
                    limit = pageSize,
                ),
            ).content
            val items = page.items.orEmpty()
            if (items.isEmpty()) break
            out += items
            if (out.size >= page.totalRecordCount) break
            startIndex += pageSize
        }
        return out
    }
}

// --- Jellyfin DTO -> domain ---

private fun BaseItemDto.toSong(api: ApiClient): Song = Song(
    id = MediaId(id.toString()),
    title = name ?: "Unknown",
    albumName = album.orEmpty(),
    artistName = artists?.joinToString(", ")?.ifBlank { null } ?: albumArtist ?: "Unknown artist",
    durationMs = (runTimeTicks ?: 0L) / 10_000,
    artworkUrl = artworkUrl(api),
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

private suspend fun BaseItemDto.toPlaylist(api: ApiClient): Playlist {
    val memberArt = runCatching {
        api.playlistsApi.getPlaylistItems(
            GetPlaylistItemsRequest(playlistId = id, startIndex = 0, limit = 4),
        ).content.items.orEmpty().mapNotNull { it.artworkUrl(api) }
    }.getOrDefault(emptyList())
    return Playlist(
        id = MediaId(id.toString()),
        name = name ?: "Untitled playlist",
        artworkUrl = artworkUrl(api),
        songCount = childCount ?: 0,
        memberArtworkUrls = memberArt,
    )
}

private fun BaseItemDto.artworkUrl(api: ApiClient): String? = runCatching {
    // Only build a URL when the item actually has a Primary image (imageTags is the source of
    // truth) — otherwise items with no art (e.g. an empty playlist) get a URL that 404s instead
    // of null, which skips the UI's placeholder. The tag is also passed through as a cache-busting
    // query param so Coil's URL-keyed cache can't serve stale bytes for a different item that
    // happens to reuse the same id (observed with recreated same-named playlists).
    val tag = imageTags?.get(ImageType.PRIMARY) ?: return@runCatching null
    // Store the base URL; callers append the size they need via sizedArtUrl (tiny for list
    // thumbnails, larger for the Now Playing hero) so lists stay cheap to scroll.
    val url = api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, tag = tag)
    // Servers requiring authenticated image access 404 without a token — the SDK doesn't add
    // one to this URL by itself (unlike getAudioStreamUrl, which StreamUrlResolverImpl patches).
    ensureApiKey(url, api.accessToken)
}.getOrNull()

// --- domain <-> Room entity ---

private fun Song.toEntity() = SongEntity(id.value, title, albumName, artistName, durationMs, artworkUrl, isFavorite)
private fun Album.toEntity() = AlbumEntity(id.value, name, artistName, artworkUrl, year)
private fun Artist.toEntity() = ArtistEntity(id.value, name, artworkUrl)
private fun Playlist.toEntity() =
    PlaylistEntity(id.value, name, artworkUrl, songCount, memberArtworkUrls.joinToString("|"))

private fun SongEntity.toSong() = Song(MediaId(id), title, albumName, artistName, durationMs, artworkUrl, isFavorite)
private fun AlbumEntity.toAlbum() = Album(MediaId(id), name, artistName, artworkUrl, year)
private fun ArtistEntity.toArtist() = Artist(MediaId(id), name, artworkUrl)
private fun PlaylistEntity.toPlaylist() = Playlist(
    MediaId(id),
    name,
    artworkUrl,
    songCount,
    if (memberArtworkUrls.isEmpty()) emptyList() else memberArtworkUrls.split("|"),
)
