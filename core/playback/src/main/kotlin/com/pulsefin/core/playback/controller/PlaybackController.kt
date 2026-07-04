package com.pulsefin.core.playback.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.playback.service.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Provider-agnostic snapshot of what the player is doing, for the UI to render. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentMediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
) {
    val hasItem: Boolean get() = currentMediaId != null
}

/**
 * Bridges the UI to the Media3 [PlaybackService] via a [MediaController]. Holds a single
 * lazily-connected controller and exposes playback as a [StateFlow]. All controller calls
 * happen on the main thread (Media3 requirement).
 */
class PlaybackController(private val context: Context) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { action(it); return }
        val future = controllerFuture ?: MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync().also { controllerFuture = it }

        future.addListener({
            val ready = future.get()
            controller = ready
            ready.addListener(listener)
            updateState(ready)
            action(ready)
        }, context.mainExecutor)
    }

    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val items = songs.mapNotNull { it.toMediaItem() }
        if (items.isEmpty()) return
        withController { controller ->
            controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    private fun updateState(player: Player) {
        val metadata = player.mediaMetadata
        _state.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentMediaId = player.currentMediaItem?.mediaId,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            artworkUrl = metadata.artworkUri?.toString(),
        )
    }
}

private fun Song.toMediaItem(): MediaItem? {
    val uri = streamUrl ?: return null
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.value)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistName)
                .setAlbumTitle(albumName)
                .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()
}
