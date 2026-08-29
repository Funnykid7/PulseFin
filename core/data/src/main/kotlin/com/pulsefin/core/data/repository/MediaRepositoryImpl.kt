package com.pulsefin.core.data.repository

import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.common.result.PulseResult
import androidx.room.withTransaction
import com.pulsefin.core.data.jellyfin.JellyfinApiProvider
import com.pulsefin.core.data.local.AlbumDao
import com.pulsefin.core.data.local.AlbumEntity
import com.pulsefin.core.data.local.ArtistDao
import com.pulsefin.core.data.local.ArtistEntity
import com.pulsefin.core.data.local.PlaylistDao
import com.pulsefin.core.data.local.PlaylistEntity
import com.pulsefin.core.data.local.PulseFinDatabase
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
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
    private val database: PulseFinDatabase,
) : MediaRepository {

    private val refreshMutex = Mutex()
    private val favoriteMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var hasSyncedThisProcess = false

    private val _lastSyncError = MutableStateFlow<Throwable?>(null)
    override fun observeLastSyncError(): Flow<Throwable?> = _lastSyncError.asStateFlow()

    // The network fetch + Room transaction in refreshLibrary() runs as a child of this scope
    // (not the caller's own coroutine) specifically so resetSyncState() can cancel a straggling
    // sync on logout, independent of whichever screen happened to have triggered it.
    private var refreshScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun resetSyncState() {
        refreshScope.coroutineContext.job.cancelAndJoin()
        refreshScope = CoroutineScope(SupervisorJob() + dispatchers.io)
        hasSyncedThisProcess = false
    }

    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAll().map { rows -> rows.map { it.toSong() } }

    override fun observeAlbums(): Flow<List<Album>> =
        albumDao.observeAll().map { rows -> rows.map { it.toAlbum() } }

    override fun observeArtists(): Flow<List<Artist>> =
        artistDao.observeAll().map { rows -> rows.map { it.toArtist() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        songDao.observeFavoriteIds().map { it.toSet() }

    override suspend fun refreshLibrary(force: Boolean): PulseResult<Unit> = withContext(dispatchers.io) {
        // A deduped no-op isn't a real sync attempt, so it shouldn't clear a real prior error —
        // only overwrite _lastSyncError below when this call actually tried to sync.
        var attemptedSync = false
        val result = apiResult {
            refreshMutex.withLock {
                // Dedupe the auto-sync the three tabs each kick off; pull-to-refresh forces it.
                if (hasSyncedThisProcess && !force) return@withLock
                attemptedSync = true
                val api = requireApi()
                // Runs as a child of refreshScope, not this call's own coroutine, so
                // resetSyncState() can cancel a straggling sync on logout — even after whichever
                // screen originally triggered it has moved on — before it can write stale data
                // into a just-wiped Room database.
                refreshScope.async {
                    // A malformed item (e.g. an enum value from a newer server than this pinned
                    // SDK understands) fails that whole page's decode, not just one item — so
                    // isolation happens per type, not per item: supervisorScope keeps one type's
                    // failure from cancelling the other three's already-in-flight fetches, and
                    // runCatching means a failed type simply keeps its last-synced Room data
                    // instead of every type's fetch being cancelled and nothing committing.
                    var firstFailure: Throwable? = null
                    supervisorScope {
                        val songsDeferred = async {
                            runCatching { fetchAllItems(api, BaseItemKind.AUDIO).map { it.toSong(api).toEntity() } }
                        }
                        val albumsDeferred = async {
                            runCatching { fetchAllItems(api, BaseItemKind.MUSIC_ALBUM).map { it.toAlbum(api).toEntity() } }
                        }
                        val artistsDeferred = async {
                            runCatching { fetchAllItems(api, BaseItemKind.MUSIC_ARTIST).map { it.toArtist(api).toEntity() } }
                        }
                        val playlistsDeferred = async {
                            runCatching {
                                fetchAllItems(api, BaseItemKind.PLAYLIST)
                                    .map { async { it.toPlaylist(api) } }
                                    .awaitAll()
                                    .map { it.toEntity() }
                            }
                        }
                        val songsResult = songsDeferred.await()
                        val albumsResult = albumsDeferred.await()
                        val artistsResult = artistsDeferred.await()
                        val playlistsResult = playlistsDeferred.await()
                        // One outer transaction spanning every table this pass actually has fresh
                        // data for — each replaceAll is itself @Transaction, but without this
                        // wrapper a crash/process-death partway through could leave Room with a
                        // mix of freshly-synced and stale tables.
                        database.withTransaction {
                            songsResult.onSuccess { songs ->
                                songDao.replaceAll(songs)
                                // Evict mutex entries for songs no longer in the library — one
                                // Mutex per distinct song ID ever toggled would otherwise sit in
                                // this map for the rest of the process's lifetime.
                                val liveIds = songs.mapTo(mutableSetOf()) { it.id }
                                favoriteMutexes.keys.retainAll(liveIds)
                            }.onFailure { firstFailure = firstFailure ?: it }
                            albumsResult.onSuccess { albumDao.replaceAll(it) }.onFailure { firstFailure = firstFailure ?: it }
                            artistsResult.onSuccess { artistDao.replaceAll(it) }.onFailure { firstFailure = firstFailure ?: it }
                            playlistsResult.onSuccess { playlistDao.replaceAll(it) }.onFailure { firstFailure = firstFailure ?: it }
                        }
                    }
                    // Surface a failure (so _lastSyncError/hasSyncedThisProcess reflect it,
                    // prompting a retry next sync) only after the successful types' fresh data is
                    // already durably committed above — this must not roll that back.
                    firstFailure?.let { throw it }
                }.await()
                hasSyncedThisProcess = true
            }
        }
        if (attemptedSync) {
            _lastSyncError.value = (result as? PulseResult.Failure)?.error
        }
        result
    }

    override suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>> =
        withContext(dispatchers.io) {
            apiResult {
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
            apiResult {
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
            apiResult {
                if (query.isBlank()) {
                    return@apiResult SearchResults(emptyList(), emptyList(), emptyList())
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
            // Serialize per song: without this, two rapid taps' optimistic-write-then-rollback
            // pairs can interleave and let an earlier call's failure rollback stomp a later call's
            // already-confirmed successful write.
            favoriteMutexes.getOrPut(songId) { Mutex() }.withLock {
                // Optimistic: update Room first so the heart flips instantly, then tell the server.
                // Wrapped (unlike a plain call) so a throw here — e.g. disk full — surfaces as
                // PulseResult.Failure like the rest of this repository's contract, instead of
                // propagating uncaught past this function's declared PulseResult return type.
                val optimisticWrite = runCatching { songDao.setFavorite(songId, favorite) }
                if (optimisticWrite.isFailure) {
                    return@withLock PulseResult.Failure(optimisticWrite.exceptionOrNull()!!)
                }
                val result = apiResult {
                    val api = requireApi()
                    val itemId = UUID.fromString(songId)
                    if (favorite) api.userLibraryApi.markFavoriteItem(itemId = itemId)
                    else api.userLibraryApi.unmarkFavoriteItem(itemId = itemId)
                    Unit
                }
                // Roll back the optimistic write on failure so Room doesn't keep lying to the user.
                // On success, re-assert it instead: a concurrent refreshLibrary()'s wholesale
                // songDao.replaceAll(songs) can land between the optimistic write above and here,
                // silently clobbering this toggle with a server snapshot from before the mark/unmark
                // call above was applied server-side. Still under this song's mutex, so this is the
                // final write regardless of when refreshLibrary's replace happened to land.
                if (result is PulseResult.Failure) songDao.setFavorite(songId, !favorite)
                else songDao.setFavorite(songId, favorite)
                result
            }
        }

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAll().map { rows -> rows.map { it.toPlaylist() } }

    override suspend fun createPlaylist(name: String, songIds: List<String>): PulseResult<Unit> =
        withContext(dispatchers.io) {
            apiResult {
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
            apiResult {
                val api = requireApi()
                api.playlistsApi.updatePlaylist(UUID.fromString(playlistId), UpdatePlaylistDto(name = name))
                upsertSinglePlaylist(api, playlistId)
            }
        }

    override suspend fun deletePlaylist(playlistId: String): PulseResult<Unit> =
        withContext(dispatchers.io) {
            apiResult {
                requireApi().libraryApi.deleteItem(UUID.fromString(playlistId))
                playlistDao.delete(playlistId)
            }
        }

    override suspend fun songsForPlaylist(playlistId: String): PulseResult<List<Song>> =
        withContext(dispatchers.io) {
            apiResult {
                val api = requireApi()
                fetchAllPlaylistItems(api, playlistId).map { item ->
                    item.toSong(api).copy(playlistItemId = item.playlistItemId)
                }
            }
        }

    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>): PulseResult<Unit> =
        withContext(dispatchers.io) {
            apiResult {
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
            apiResult {
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
            apiResult {
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
            apiResult {
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

    /**
     * [PulseResult.runCatchingResult] plus 401 detection: a server-side session revocation
     * (password change, revoked API key, expiry policy) otherwise surfaces as just another
     * generic Failure, with nothing ever transitioning AuthState away from LoggedIn.
     */
    private suspend inline fun <T> apiResult(block: () -> T): PulseResult<T> =
        PulseResult.runCatchingResult(block).also { result ->
            if (result is PulseResult.Failure) apiProvider.invalidateSessionIfUnauthorized(result.error)
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
            // Advance by what was actually returned, not the requested page size — a
            // server-enforced cap or a transient short page would otherwise permanently skip
            // whatever items sat between this response's end and the next full-size page start.
            startIndex += items.size
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
            // Advance by what was actually returned, not the requested page size — a
            // server-enforced cap or a transient short page would otherwise permanently skip
            // whatever items sat between this response's end and the next full-size page start.
            startIndex += items.size
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
    // Store the base URL only — deliberately WITHOUT an api_key, since this value is persisted
    // verbatim to Room (and, via the playback queue, to on-disk queue state). Servers requiring
    // authenticated image access 404 without a token, so callers that actually fetch the image
    // (Coil, the playback MediaItem) attach one fresh via StreamUrlResolver.resolveArtworkUrl.
    // Callers also append the size they need via sizedArtUrl (tiny for list thumbnails, larger
    // for the Now Playing hero) so lists stay cheap to scroll.
    api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, tag = tag)
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
