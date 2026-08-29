package com.pulsefin.app.ui.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.HapticEffect
import com.pulsefin.app.ui.components.LocalHapticsEnabled
import com.pulsefin.app.ui.components.performHaptic

/**
 * Material 3 Expressive seek bar: a determinate [LinearWavyProgressIndicator] whose track waves
 * while playing and flattens when paused, with a tap/drag overlay for scrubbing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    // A track change mid-drag restarts the pointerInput(durationMs) blocks below without firing
    // onDragEnd/onDragCancel, which would otherwise leave scrub stuck at the old track's position.
    LaunchedEffect(durationMs) { scrub = null }
    val playbackFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction = scrub ?: playbackFraction
    val view = LocalView.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val seekBarDescription = stringResource(R.string.cd_seek_bar)
    val seekForwardLabel = stringResource(R.string.action_seek_forward)
    val seekBackwardLabel = stringResource(R.string.action_seek_backward)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                // detectTapGestures/detectHorizontalDragGestures below are unreachable via
                // TalkBack (it intercepts single-finger drags for its own navigation), so this is
                // the app's only seek control that needs an accessible alternative to reach every
                // position, not just fixed-step nudges.
                .semantics(mergeDescendants = true) {
                    contentDescription = seekBarDescription
                    if (durationMs > 0) {
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        customActions = listOf(
                            CustomAccessibilityAction(seekForwardLabel) {
                                onSeek((positionMs + SEEK_STEP_MS).coerceAtMost(durationMs))
                                true
                            },
                            CustomAccessibilityAction(seekBackwardLabel) {
                                onSeek((positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
                                true
                            },
                        )
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            view.performHaptic(HapticEffect.Medium, hapticsEnabled)
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((f * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            view.performHaptic(HapticEffect.Medium, hapticsEnabled)
                            scrub = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val f = scrub
                            if (f != null && durationMs > 0) onSeek((f * durationMs).toLong())
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                amplitude = { if (isPlaying) 1f else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime((fraction * durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SEEK_STEP_MS = 10_000L

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(java.util.Locale.US, minutes, seconds)
}
