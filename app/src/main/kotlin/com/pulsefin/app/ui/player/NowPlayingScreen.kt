package com.pulsefin.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulsefin.app.ui.components.AnimatedPlayPauseIcon
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.designsystem.theme.RoundedHeroShape
import com.pulsefin.core.designsystem.theme.cookieShape
import com.pulsefin.core.playback.controller.PlaybackController
import org.koin.compose.koinInject

/** Full-screen player: large art, metadata, the wavy seek bar, and transport controls. */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    playbackController: PlaybackController = koinInject(),
) {
    val state by playbackController.state.collectAsStateWithLifecycle()
    val positionMs by playbackController.positionMs.collectAsStateWithLifecycle()

    // Re-theme the whole screen to the current track's album art (per-screen Monet). The scheme is
    // hoisted in the nav host and shared with the mini-player, so it's already present when we open
    // (no color snap after the expand) and survives the collapse.
    ArtworkTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NowPlayingContent(
                state = state,
                positionMs = positionMs,
                onCollapse = onCollapse,
                onOpenQueue = onOpenQueue,
                playbackController = playbackController,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingContent(
    state: com.pulsefin.core.playback.controller.PlaybackState,
    positionMs: Long,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    playbackController: PlaybackController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = { if (totalDrag > 220f) onCollapse() },
                )
            }
            // Use stable system-bar insets, not the Scaffold's contentPadding: the bottom bar
            // slides (no height animation) so its padding snaps in one frame at the transition
            // boundary, which would jerk this full-screen layout right as it settles.
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val collapseInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.pressScale(collapseInteraction, pressedScale = 0.9f),
                interactionSource = collapseInteraction,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse")
            }
            val queueInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.pressScale(queueInteraction, pressedScale = 0.9f),
                interactionSource = queueInteraction,
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
            }
        }

        Spacer(Modifier.weight(1f))

        // The art breathes with playback: full size while playing, gently recedes when paused.
        val artScale by animateFloatAsState(
            targetValue = if (state.isPlaying) 1f else 0.94f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "artScale",
        )
        AsyncImage(
            // Bridge with the mini-player's already-cached thumbnail so the hero shows art from
            // the first frame of the expand instead of an empty box that hard-pops (crossfade is
            // off globally) once the full-res decode lands after the slide finishes.
            model = ImageRequest.Builder(LocalContext.current)
                .data(state.artworkUrl)
                .placeholderMemoryCacheKey(state.artworkUrl)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }
                .clip(RoundedHeroShape),
        )

        Spacer(Modifier.size(28.dp))

        Text(
            text = state.title.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = state.artist.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(28.dp))

        WavySeekBar(
            positionMs = positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            onSeek = playbackController::seekTo,
        )

        Spacer(Modifier.size(16.dp))

        val shuffleTint by animateColorAsState(
            if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "shuffleTint",
        )
        val repeatTint by animateColorAsState(
            if (state.isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "repeatTint",
        )
        val shuffleInteraction = remember { MutableInteractionSource() }
        val prevInteraction = remember { MutableInteractionSource() }
        val playInteraction = remember { MutableInteractionSource() }
        val nextInteraction = remember { MutableInteractionSource() }
        val repeatInteraction = remember { MutableInteractionSource() }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = playbackController::toggleShuffle,
                modifier = Modifier.pressScale(shuffleInteraction),
                interactionSource = shuffleInteraction,
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = shuffleTint)
            }
            IconButton(
                onClick = playbackController::previous,
                enabled = state.hasPrevious,
                modifier = Modifier.pressScale(prevInteraction),
                interactionSource = prevInteraction,
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                )
            }
            FilledIconButton(
                onClick = playbackController::togglePlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .pressScale(playInteraction),
                shape = cookieShape(),
                colors = IconButtonDefaults.filledIconButtonColors(),
                interactionSource = playInteraction,
            ) {
                AnimatedPlayPauseIcon(
                    isPlaying = state.isPlaying,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(
                onClick = playbackController::next,
                enabled = state.hasNext,
                modifier = Modifier.pressScale(nextInteraction),
                interactionSource = nextInteraction,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(
                onClick = playbackController::cycleRepeat,
                modifier = Modifier.pressScale(repeatInteraction),
                interactionSource = repeatInteraction,
            ) {
                Icon(
                    imageVector = if (state.isRepeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = repeatTint,
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
