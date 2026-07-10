package com.pulsefin.app.playback

import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L

/**
 * Bridges the provider-agnostic [PlaybackController] to Jellyfin's playstateApi, so play
 * history / "now playing" show up on the server and other clients. Lives in :app rather than
 * :core:playback so the transport layer stays provider-agnostic (per PlaybackService's own doc
 * comment) — reporting to the server is a sync concern that belongs with [MediaRepository].
 */
class PlaybackScrobbler(
    private val playbackController: PlaybackController,
    private val repository: MediaRepository,
) {
    // One session ID for the whole app process, matching how Jellyfin clients group a
    // continuous listening session across multiple tracks, not one ID per track.
    private val playSessionId = UUID.randomUUID().toString()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var reportedMediaId: String? = null
    private var lastProgressReportMs = 0L

    fun start() {
        scope.launch {
            playbackController.state.collect { state ->
                val mediaId = state.currentMediaId
                if (mediaId != reportedMediaId) {
                    reportedMediaId?.let { previous ->
                        repository.reportPlaybackStopped(previous, playSessionId, playbackController.positionMs.value)
                    }
                    reportedMediaId = mediaId
                    lastProgressReportMs = 0L
                    if (mediaId != null) repository.reportPlaybackStart(mediaId, playSessionId)
                } else if (mediaId != null) {
                    // Same track, but play/pause (or shuffle/repeat) changed — ping immediately
                    // so "now playing" on other clients reflects the paused state right away.
                    repository.reportPlaybackProgress(
                        mediaId,
                        playSessionId,
                        playbackController.positionMs.value,
                        !state.isPlaying,
                    )
                }
            }
        }
        scope.launch {
            playbackController.positionMs.collect { positionMs ->
                val mediaId = reportedMediaId ?: return@collect
                if (positionMs - lastProgressReportMs < PROGRESS_REPORT_INTERVAL_MS) return@collect
                lastProgressReportMs = positionMs
                repository.reportPlaybackProgress(
                    mediaId,
                    playSessionId,
                    positionMs,
                    !playbackController.state.value.isPlaying,
                )
            }
        }
    }
}
