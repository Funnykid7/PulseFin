package com.pulsefin.core.data.repository

import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.data.jellyfin.JellyfinApiProvider
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.model.MediaId
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.domain.repository.SearchResults
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/**
 * Reads the music library from Jellyfin via the official SDK. Fetched directly from the
 * network for now (Room mirroring / offline is a later increment).
 */
class MediaRepositoryImpl(
    private val apiProvider: JellyfinApiProvider,
    private val dispatchers: AppDispatchers,
) : MediaRepository {

    override suspend fun songs(limit: Int): PulseResult<List<Song>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.itemsApi.getItems(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        limit = limit,
                    ),
                ).content.items.orEmpty().map { it.toSong(api) }
            }
        }

    override suspend fun albums(): PulseResult<List<Album>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.itemsApi.getItems(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    ),
                ).content.items.orEmpty().map { it.toAlbum(api) }
            }
        }

    override suspend fun artists(): PulseResult<List<Artist>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = requireApi()
                api.itemsApi.getItems(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    ),
                ).content.items.orEmpty().map { it.toArtist(api) }
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
        PulseResult.Success(SearchResults(artists = emptyList(), albums = emptyList(), songs = emptyList()))

    private suspend fun requireApi(): ApiClient = apiProvider.api() ?: error("Not signed in")
}

private fun BaseItemDto.toSong(api: ApiClient): Song = Song(
    id = MediaId(id.toString()),
    title = name ?: "Unknown",
    albumName = album.orEmpty(),
    artistName = artists?.joinToString(", ")?.ifBlank { null } ?: albumArtist ?: "Unknown artist",
    durationMs = (runTimeTicks ?: 0L) / 10_000,
    artworkUrl = artworkUrl(api),
    streamUrl = ensureApiKey(api.audioApi.getAudioStreamUrl(itemId = id, static = true), api.accessToken),
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
    api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY)
}.getOrNull()

/** Direct-play URLs must carry auth for ExoPlayer; append the token if the SDK didn't. */
private fun ensureApiKey(url: String, token: String?): String {
    if (token.isNullOrBlank() || url.contains("api_key=", ignoreCase = true)) return url
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}api_key=$token"
}
