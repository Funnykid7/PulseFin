package com.pulsefin.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.AnimatedPlayPauseIcon
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.playback.controller.PlaybackController
import com.pulsefin.core.playback.controller.PlaybackState

/** Floating now-playing card. Renders nothing until something is loaded into the player. */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    playbackController: PlaybackController,
    modifier: Modifier = Modifier,
) {
    if (!state.hasItem) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .bouncyClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = state.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SquircleShape),
                )
                // Crossfade + drift the labels on track change; capture the strings in the target
                // so the outgoing content keeps showing the previous track while it animates away.
                AnimatedContent(
                    targetState = Triple(state.currentMediaId, state.title.orEmpty(), state.artist.orEmpty()),
                    contentKey = { it.first },
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 4 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 4 })
                    },
                    label = "miniTrack",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) { (_, title, artist) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (state.error != null) stringResource(R.string.miniplayer_error) else artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val playInteraction = remember { MutableInteractionSource() }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.pressScale(playInteraction),
                    interactionSource = playInteraction,
                ) {
                    AnimatedPlayPauseIcon(
                        isPlaying = state.isPlaying,
                        contentDescription = stringResource(if (state.isPlaying) R.string.state_pause else R.string.action_play),
                    )
                }
            }
            MiniPlayerProgress(
                playbackController = playbackController,
                durationMs = state.durationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

/**
 * Collects the 500ms position tick in its own composable scope so it doesn't drag the rest of
 * [MiniPlayer] (artwork, title, play/pause button) into that recomposition — see
 * [com.pulsefin.core.playback.controller.PlaybackController]'s "Position is ticked separately"
 * comment, which this mirrors at the UI layer.
 */
@Composable
private fun MiniPlayerProgress(
    playbackController: PlaybackController,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val positionMs by playbackController.positionMs.collectAsStateWithLifecycle()
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
