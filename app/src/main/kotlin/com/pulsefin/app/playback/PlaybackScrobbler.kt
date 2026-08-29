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
    // Groups a continuous listening session across multiple tracks, matching how Jellyfin
    // clients report "now playing" — not one ID per track. Regenerated on login/logout via
    // resetSession() so two different users in the same process lifetime never share one id.
    private var playSessionId = UUID.randomUUID().toString()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Call on login/logout so a new session (possibly a different user) gets its own id. */
    fun resetSession() {
        playSessionId = UUID.randomUUID().toString()
        reportedMediaId = null
        lastProgressReportMs = 0L
        lastKnownPositionMs = 0L
    }

    private var reportedMediaId: String? = null
    private var lastProgressReportMs = 0L

    // Tracks the outgoing track's own position, not read from PlaybackController.positionMs.value
    // directly — by the time a track-transition is observed here, positionMs has already been
    // overwritten with the *new* track's position (both StateFlows are updated together, from the
    // same Player.Listener.onEvents() call). This field is only ever advanced by the positionMs
    // collector below, so it still holds the finished track's last-seen position at the instant the
    // state collector below detects the transition and reads it, keeping the two in step.
    private var lastKnownPositionMs = 0L

    fun start() {
        scope.launch {
            playbackController.state.collect { state ->
                val mediaId = state.currentMediaId
                if (mediaId != reportedMediaId) {
                    reportedMediaId?.let { previous ->
                        repository.reportPlaybackStopped(previous, playSessionId, lastKnownPositionMs)
                    }
                    reportedMediaId = mediaId
                    lastProgressReportMs = 0L
                    lastKnownPositionMs = 0L
                    if (mediaId != null) repository.reportPlaybackStart(mediaId, playSessionId)
                } else if (mediaId != null) {
                    // Same track, but play/pause (or shuffle/repeat) changed — ping immediately
                    // so "now playing" on other clients reflects the paused state right away.
                    repository.reportPlaybackProgress(
                        mediaId,
                        playSessionId,
                        lastKnownPositionMs,
                        !state.isPlaying,
                    )
                    // Otherwise the periodic collector below fires again moments later at an
                    // almost-identical position, since it only measures elapsed position, not time.
                    lastProgressReportMs = lastKnownPositionMs
                }
            }
        }
        scope.launch {
            playbackController.positionMs.collect { positionMs ->
                lastKnownPositionMs = positionMs
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
