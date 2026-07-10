package com.pulsefin.core.playback.queue

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.queueStateDataStore by preferencesDataStore(name = "pulsefin_queue_state")

data class PersistedQueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String,
)

data class PersistedQueueState(
    val items: List<PersistedQueueItem>,
    val currentIndex: Int,
    val positionMs: Long,
)

fun PersistedQueueItem.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(streamUrl)
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()

/**
 * Persists just enough of the live queue (not the full [com.pulsefin.core.domain.model.Song],
 * to avoid a `:core:playback` -> `:core:data` dependency) to rebuild it on
 * [com.pulsefin.core.playback.service.PlaybackService.onCreate] after process death.
 */
class QueueStateStore(private val context: Context) {

    private object Keys {
        val ITEMS = stringPreferencesKey("items")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val POSITION_MS = longPreferencesKey("position_ms")
    }

    suspend fun save(state: PersistedQueueState) {
        context.queueStateDataStore.edit { prefs ->
            val array = JSONArray()
            state.items.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.mediaId)
                        .put("title", item.title)
                        .put("artist", item.artist)
                        .put("album", item.album)
                        .put("art", item.artworkUrl.orEmpty())
                        .put("stream", item.streamUrl),
                )
            }
            prefs[Keys.ITEMS] = array.toString()
            prefs[Keys.CURRENT_INDEX] = state.currentIndex
            prefs[Keys.POSITION_MS] = state.positionMs
        }
    }

    suspend fun load(): PersistedQueueState? {
        val prefs = context.queueStateDataStore.data.first()
        val raw = prefs[Keys.ITEMS] ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val items = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            PersistedQueueItem(
                mediaId = obj.optString("id"),
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                album = obj.optString("album"),
                artworkUrl = obj.optString("art").ifBlank { null },
                streamUrl = obj.optString("stream"),
            )
        }
        if (items.isEmpty()) return null
        return PersistedQueueState(
            items = items,
            currentIndex = prefs[Keys.CURRENT_INDEX] ?: 0,
            positionMs = prefs[Keys.POSITION_MS] ?: 0L,
        )
    }
}
