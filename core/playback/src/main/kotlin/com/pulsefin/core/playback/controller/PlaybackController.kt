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
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val currentIndex: Int = -1,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
) {
    val hasItem: Boolean get() = currentMediaId != null
    val isRepeatActive: Boolean get() = repeatMode != Player.REPEAT_MODE_OFF
    val isRepeatOne: Boolean get() = repeatMode == Player.REPEAT_MODE_ONE
}

/** A single entry in the play queue. */
data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
)

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

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    // Sleep timer: milliseconds until playback auto-pauses, or null when no timer is set.
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()
    private var sleepJob: Job? = null

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var ticking = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
            updateQueue(player)
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

    fun toggleShuffle() {
        withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        withController {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Auto-pause after [minutes]. Replaces any running timer. */
    fun startSleepTimer(minutes: Int) = startSleepTimerFor(minutes * 60_000L)

    /** Auto-pause when the current track finishes. */
    fun startSleepTimerAtTrackEnd() {
        withController { c ->
            val duration = c.duration
            if (duration == C.TIME_UNSET || duration <= 0L) return@withController
            startSleepTimerFor((duration - c.currentPosition).coerceAtLeast(0L))
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }

    private fun startSleepTimerFor(durationMs: Long) {
        sleepJob?.cancel()
        if (durationMs <= 0L) {
            _sleepRemainingMs.value = null
            return
        }
        sleepJob = scope.launch {
            var remaining = durationMs
            while (isActive && remaining > 0L) {
                _sleepRemainingMs.value = remaining
                delay(1000L)
                remaining -= 1000L
            }
            if (isActive) {
                controller?.pause()
                _sleepRemainingMs.value = null
                sleepJob = null
            }
        }
    }

    fun playIndex(index: Int) {
        withController { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.seekToDefaultPosition(index)
                controller.play()
            }
        }
    }

    private fun updateQueue(player: Player) {
        _queue.value = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            val md = item.mediaMetadata
            QueueItem(
                mediaId = item.mediaId,
                title = md.title?.toString() ?: "Unknown",
                artist = md.artist?.toString().orEmpty(),
                artworkUrl = md.artworkUri?.toString(),
            )
        }
    }

    private fun updateState(player: Player) {
        val metadata = player.mediaMetadata
        val duration = player.duration
        _state.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentMediaId = player.currentMediaItem?.mediaId,
            currentIndex = player.currentMediaItemIndex,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            artworkUrl = metadata.artworkUri?.toString(),
            durationMs = if (duration == C.TIME_UNSET || duration < 0) 0L else duration,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
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
                .apply { sizedArtUrl(artworkUrl, 720)?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()
}
