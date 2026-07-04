package com.pulsefin.core.playback.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Provider-agnostic snapshot of what the player is doing, for the UI to render. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentMediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val hasItem: Boolean get() = currentMediaId != null
}

/**
 * Bridges the UI to the Media3 [PlaybackService] via a [MediaController]. Holds a single
 * lazily-connected controller and exposes playback as a [StateFlow]. All controller calls
 * happen on the main thread (Media3 requirement).
 */
class PlaybackController(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Position is ticked separately so per-second updates don't recompose everything.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var ticking = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
            _positionMs.value = player.currentPosition.coerceAtLeast(0L)
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
            startTicking()
            action(ready)
        }, context.mainExecutor)
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        scope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                }
                delay(500L)
            }
        }
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

    fun seekTo(positionMs: Long) {
        withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun next() {
        withController { it.seekToNextMediaItem() }
    }

    fun previous() {
        withController { it.seekToPreviousMediaItem() }
    }

    private fun updateState(player: Player) {
        val metadata = player.mediaMetadata
        val duration = player.duration
        _state.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentMediaId = player.currentMediaItem?.mediaId,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            artworkUrl = metadata.artworkUri?.toString(),
            durationMs = if (duration == C.TIME_UNSET || duration < 0) 0L else duration,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
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
