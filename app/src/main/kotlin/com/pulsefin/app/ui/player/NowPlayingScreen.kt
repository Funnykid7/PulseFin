package com.pulsefin.app.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.designsystem.theme.RoundedHeroShape
import com.pulsefin.core.designsystem.theme.cookieShape
import com.pulsefin.core.playback.controller.PlaybackController
import org.koin.compose.koinInject

/** Full-screen player: large art, metadata, the wavy seek bar, and transport controls. */
@Composable
fun NowPlayingScreen(
    contentPadding: PaddingValues,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    playbackController: PlaybackController = koinInject(),
) {
    val state by playbackController.state.collectAsStateWithLifecycle()
    val positionMs by playbackController.positionMs.collectAsStateWithLifecycle()

    // Re-theme the whole screen to the current track's album art (per-screen Monet).
    ArtworkTheme(state.artworkUrl) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NowPlayingContent(
                state = state,
                positionMs = positionMs,
                contentPadding = contentPadding,
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
    contentPadding: PaddingValues,
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
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse")
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
            }
        }

        Spacer(Modifier.weight(1f))

        AsyncImage(
            model = state.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = playbackController::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = playbackController::previous, enabled = state.hasPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                )
            }
            FilledIconButton(
                onClick = playbackController::togglePlayPause,
                modifier = Modifier.size(72.dp),
                shape = cookieShape(),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = playbackController::next, enabled = state.hasNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = playbackController::cycleRepeat) {
                Icon(
                    imageVector = if (state.isRepeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (state.isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
