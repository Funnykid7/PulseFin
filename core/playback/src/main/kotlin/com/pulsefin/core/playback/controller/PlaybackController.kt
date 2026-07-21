package com.pulsefin.core.playback.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.StreamUrlResolver
import com.pulsefin.core.playback.queue.PersistedQueueItem
import com.pulsefin.core.playback.queue.PersistedQueueState
import com.pulsefin.core.playback.queue.QueueStateStore
import com.pulsefin.core.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val error: PlaybackError? = null,
) {
    val hasItem: Boolean get() = currentMediaId != null
    val isRepeatActive: Boolean get() = repeatMode != Player.REPEAT_MODE_OFF
    val isRepeatOne: Boolean get() = repeatMode == Player.REPEAT_MODE_ONE
}

/** Provider-agnostic classification of a playback failure, for the UI to render a message. */
sealed interface PlaybackError {
    data object Network : PlaybackError
    data object Auth : PlaybackError
    data object NotFound : PlaybackError
    data object Unknown : PlaybackError
}

private val NETWORK_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
)

private fun PlaybackException.toDomainError(): PlaybackError {
    val httpCause = cause as? HttpDataSource.InvalidResponseCodeException
    return when {
        httpCause?.responseCode == 401 -> PlaybackError.Auth
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            httpCause?.responseCode == 404 -> PlaybackError.NotFound
        errorCode in NETWORK_ERROR_CODES || errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            PlaybackError.Network
        else -> PlaybackError.Unknown
    }
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
class PlaybackController(
    private val context: Context,
    private val queueStateStore: QueueStateStore,
    private val streamUrlResolver: StreamUrlResolver,
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Position is ticked separately so per-second updates don't recompose everything.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    // Set right after playNext() moves/inserts a song, so QueueScreen can scroll to and highlight
    // it — otherwise the move happens with no visible confirmation. Cleared once consumed.
    private val _lastMovedMediaId = MutableStateFlow<String?>(null)
    val lastMovedMediaId: StateFlow<String?> = _lastMovedMediaId.asStateFlow()

    fun consumeLastMoved() {
        _lastMovedMediaId.value = null
    }

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

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            // The session died under us (e.g. the service was killed). Drop both the controller
            // and its future so the next withController() call reconnects instead of calling
            // methods on a stale, disconnected controller.
            this@PlaybackController.controller = null
            this@PlaybackController.controllerFuture = null
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { action(it); return }
        val future = controllerFuture ?: MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).setListener(controllerListener).buildAsync().also { controllerFuture = it }

        future.addListener({
            try {
                val ready = future.get()
                controller = ready
                ready.addListener(listener)
                updateState(ready)
                startTicking()
                action(ready)
            } catch (e: Exception) {
                // The connection itself failed (session rejected us, background-start
                // restriction, etc). Drop the future so the next call rebuilds a fresh one
                // instead of forever replaying this same failure.
                controllerFuture = null
                _state.value = _state.value.copy(error = PlaybackError.Unknown)
            }
        }, context.mainExecutor)
    }

    /**
     * Recovers from a playback error. Auth errors (401) mean the queued items' stream URLs —
     * which have an access token baked in — are stale, so re-preparing the same URLs would just
     * fail again; those are re-resolved and swapped in first. Other errors (e.g. a network blip)
     * just need the existing item re-prepared.
     */
    suspend fun retry() {
        if (_state.value.error == PlaybackError.Auth) {
            val current = controller
            if (current != null) {
                // Capture the currently-playing item's id/position before resolution: a dropped
                // (unresolvable) earlier item would otherwise shift every later index left,
                // silently resuming playback on the wrong track.
                val currentMediaId = current.currentMediaItem?.mediaId
                val currentPositionMs = current.currentPosition.coerceAtLeast(0L)
                val refreshed = (0 until current.mediaItemCount).mapNotNull { i ->
                    val item = current.getMediaItemAt(i)
                    streamUrlResolver.resolveStreamUrl(item.mediaId)?.let { item.withRefreshedUri(it) }
                }
                if (refreshed.isNotEmpty()) {
                    val resolvedIndex = refreshed.indexOfFirst { it.mediaId == currentMediaId }
                        .takeIf { it >= 0 } ?: 0
                    withController { it.replaceMediaItems(0, current.mediaItemCount, refreshed) }
                    withController { it.seekTo(resolvedIndex, currentPositionMs) }
                }
            }
        }
        withController { controller ->
            controller.prepare()
            controller.play()
        }
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

    suspend fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        // Capture the target song's id before resolving: resolution can drop entries (a failed
        // lookup returns null), which would otherwise shift startIndex onto the wrong track.
        val targetMediaId = songs.getOrNull(startIndex)?.id?.value
        val items = coroutineScope {
            songs.map { async { it.toMediaItem(streamUrlResolver) } }.awaitAll()
        }.filterNotNull()
        if (items.isEmpty()) return
        val resolvedIndex = items.indexOfFirst { it.mediaId == targetMediaId }.takeIf { it >= 0 } ?: 0
        withController { controller ->
            controller.setMediaItems(items, resolvedIndex, 0L)
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

    /**
     * Makes [song] play right after the current item, without disturbing playback. If it's
     * already somewhere in the queue, moves that existing entry instead of inserting a duplicate.
     */
    suspend fun playNext(song: Song) {
        val item = song.toMediaItem(streamUrlResolver) ?: return
        withController { controller ->
            val insertAt = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
            val existingIndex = (0 until controller.mediaItemCount)
                .firstOrNull { controller.getMediaItemAt(it).mediaId == item.mediaId }
            if (existingIndex != null) {
                // moveMediaItem's target index is relative to the timeline *after* the source item
                // is removed, so a target past the removal point shifts left by one.
                val target = if (existingIndex < insertAt) insertAt - 1 else insertAt
                if (existingIndex != target) controller.moveMediaItem(existingIndex, target)
            } else {
                controller.addMediaItem(insertAt, item)
            }
            _lastMovedMediaId.value = song.id.value
        }
    }

    /** Appends [song] to the end of the queue. */
    suspend fun addToQueue(song: Song) {
        val item = song.toMediaItem(streamUrlResolver) ?: return
        withController { it.addMediaItem(item) }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        withController { it.moveMediaItem(fromIndex, toIndex) }
    }

    fun removeFromQueue(index: Int) {
        withController { it.removeMediaItem(index) }
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
        persistQueueState(player)
    }

    // Fires on discrete events (track transitions, play/pause, seeks, queue edits) — not every
    // position tick — so this is cheap enough to call unconditionally from updateQueue().
    private fun persistQueueState(player: Player) {
        val items = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            val md = item.mediaMetadata
            PersistedQueueItem(
                mediaId = item.mediaId,
                title = md.title?.toString() ?: "Unknown",
                artist = md.artist?.toString().orEmpty(),
                album = md.albumTitle?.toString().orEmpty(),
                artworkUrl = md.artworkUri?.toString(),
            )
        }
        // Persist the empty state too — otherwise clearing the queue never saves, and the last
        // non-empty queue keeps coming back on relaunch.
        val state = PersistedQueueState(
            items = items,
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
        )
        scope.launch { queueStateStore.save(state) }
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
            error = player.playerError?.toDomainError(),
        )
    }
}

/** Rebuilds this item with a freshly-resolved stream URL, keeping id/cache key/metadata intact. */
private fun MediaItem.withRefreshedUri(newUri: String): MediaItem =
    buildUpon().setUri(newUri).build()

private suspend fun Song.toMediaItem(resolver: StreamUrlResolver): MediaItem? {
    val uri = resolver.resolveStreamUrl(id.value) ?: return null
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.value)
        // Jellyfin stream URLs embed a dynamic auth token, so the URL itself can't be the cache
        // key (Media3's default) — a token refresh would otherwise orphan a downloaded track's
        // cache entry. Pin the key to the stable media id instead.
        .setCustomCacheKey(id.value)
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
