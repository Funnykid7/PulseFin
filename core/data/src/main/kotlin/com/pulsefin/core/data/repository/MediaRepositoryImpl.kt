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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

/**
 * Reads the music library from Jellyfin via the official SDK. For this increment songs are
 * fetched directly from the network (Room mirroring / offline is a later increment). Album,
 * artist and search browsing remain stubs until their features land.
 */
class MediaRepositoryImpl(
    private val apiProvider: JellyfinApiProvider,
    private val dispatchers: AppDispatchers,
) : MediaRepository {

    override fun albums(): Flow<List<Album>> = emptyFlow()

    override fun artists(): Flow<List<Artist>> = emptyFlow()

    override suspend fun songs(limit: Int): PulseResult<List<Song>> =
        withContext(dispatchers.io) {
            PulseResult.runCatchingResult {
                val api = apiProvider.api() ?: error("Not signed in")
                val items = api.itemsApi.getItems(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        limit = limit,
                    ),
                ).content.items.orEmpty()
                items.map { it.toSong(api) }
            }
        }

    override suspend fun search(query: String): PulseResult<SearchResults> =
        PulseResult.Success(SearchResults(artists = emptyList(), albums = emptyList(), songs = emptyList()))

    override suspend fun songsForAlbum(albumId: String): PulseResult<List<Song>> =
        PulseResult.Success(emptyList())
}

private fun BaseItemDto.toSong(api: ApiClient): Song {
    val streamUrl = api.audioApi.getAudioStreamUrl(itemId = id, static = true)
    val artUrl = runCatching {
        api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY)
    }.getOrNull()
    return Song(
        id = MediaId(id.toString()),
        title = name ?: "Unknown",
        albumName = album.orEmpty(),
        artistName = artists?.joinToString(", ")?.ifBlank { null }
            ?: albumArtist
            ?: "Unknown artist",
        durationMs = (runTimeTicks ?: 0L) / 10_000,
        artworkUrl = artUrl,
        streamUrl = ensureApiKey(streamUrl, api.accessToken),
    )
}

/** Direct-play URLs must carry auth for ExoPlayer; append the token if the SDK didn't. */
private fun ensureApiKey(url: String, token: String?): String {
    if (token.isNullOrBlank() || url.contains("api_key=", ignoreCase = true)) return url
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}api_key=$token"
}
