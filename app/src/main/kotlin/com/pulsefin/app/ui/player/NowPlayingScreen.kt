package com.pulsefin.app.ui.player

import android.view.HapticFeedbackConstants
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulsefin.app.ui.components.AnimatedPlayPauseIcon
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.designsystem.theme.RoundedHeroShape
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.designsystem.theme.cookieShape
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import com.pulsefin.core.playback.controller.PlaybackError
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Full-screen player: large art, metadata, the wavy seek bar, and transport controls. */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    playbackController: PlaybackController = koinInject(),
    repository: MediaRepository = koinInject(),
) {
    val state by playbackController.state.collectAsStateWithLifecycle()
    val positionMs by playbackController.positionMs.collectAsStateWithLifecycle()
    // Create the favorites flow ONCE; calling observeFavoriteIds() inline would build a new Flow
    // every recomposition (Now Playing recomposes ~2x/sec from the position tick), re-subscribing
    // the Room query each time and causing enough jank to drop taps on the transport buttons.
    val favoriteIdsFlow = remember(repository) { repository.observeFavoriteIds() }
    val favoriteIds by favoriteIdsFlow.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    val isFavorite = state.currentMediaId?.let { it in favoriteIds } ?: false

    // Re-theme the whole screen to the current track's album art (per-screen Monet). The scheme is
    // hoisted in the nav host and shared with the mini-player, so it's already present when we open
    // (no color snap after the expand) and survives the collapse.
    ArtworkTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NowPlayingContent(
                state = state,
                positionMs = positionMs,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    val id = state.currentMediaId ?: return@NowPlayingContent
                    val target = !isFavorite
                    scope.launch { repository.setFavorite(id, target) }
                },
                onCollapse = onCollapse,
                onOpenQueue = onOpenQueue,
                onOpenLyrics = onOpenLyrics,
                playbackController = playbackController,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingContent(
    state: com.pulsefin.core.playback.controller.PlaybackState,
    positionMs: Long,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    playbackController: PlaybackController,
) {
    val sleepRemainingMs by playbackController.sleepRemainingMs.collectAsStateWithLifecycle()
    var showSleepSheet by remember { mutableStateOf(false) }
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (totalDrag > 220f) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCollapse()
                        }
                    },
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val collapseInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.pressScale(collapseInteraction, pressedScale = 0.9f),
                interactionSource = collapseInteraction,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lyricsInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onOpenLyrics,
                    modifier = Modifier.pressScale(lyricsInteraction, pressedScale = 0.9f),
                    interactionSource = lyricsInteraction,
                ) {
                    Icon(Icons.Filled.Lyrics, contentDescription = "Lyrics")
                }
                val sleepInteraction = remember { MutableInteractionSource() }
                val sleepActive = sleepRemainingMs != null
                IconButton(
                    onClick = { showSleepSheet = true },
                    modifier = Modifier.pressScale(sleepInteraction, pressedScale = 0.9f),
                    interactionSource = sleepInteraction,
                ) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (sleepActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
        }

        val currentError = state.error
        if (currentError != null) {
            PlaybackErrorBanner(
                error = currentError,
                onRetry = playbackController::retry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        if (showSleepSheet) {
            SleepTimerSheet(
                remainingMs = sleepRemainingMs,
                onDismiss = { showSleepSheet = false },
                onPick = { minutes ->
                    playbackController.startSleepTimer(minutes)
                    showSleepSheet = false
                },
                onPickTrackEnd = {
                    playbackController.startSleepTimerAtTrackEnd()
                    showSleepSheet = false
                },
                onCancelTimer = {
                    playbackController.cancelSleepTimer()
                    showSleepSheet = false
                },
            )
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
        val favoriteTint by animateColorAsState(
            if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "favoriteTint",
        )
        val shuffleInteraction = remember { MutableInteractionSource() }
        val prevInteraction = remember { MutableInteractionSource() }
        val playInteraction = remember { MutableInteractionSource() }
        val nextInteraction = remember { MutableInteractionSource() }
        val repeatInteraction = remember { MutableInteractionSource() }
        val favoriteInteraction = remember { MutableInteractionSource() }

        // Transport: prev/next are tonal squircles flanking the scalloped cookie play button.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = playbackController::previous,
                enabled = state.hasPrevious,
                modifier = Modifier
                    .size(56.dp)
                    .pressScale(prevInteraction),
                shape = SquircleShape,
                interactionSource = prevInteraction,
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp),
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
            FilledTonalIconButton(
                onClick = playbackController::next,
                enabled = state.hasNext,
                modifier = Modifier
                    .size(56.dp)
                    .pressScale(nextInteraction),
                shape = SquircleShape,
                interactionSource = nextInteraction,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.size(16.dp))

        // Secondary controls grouped in one stadium pill: shuffle · repeat · favorite.
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = state.hasItem,
                    modifier = Modifier.pressScale(favoriteInteraction),
                    interactionSource = favoriteInteraction,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = favoriteTint,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PlaybackErrorBanner(
    error: PlaybackError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error.toMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

private fun PlaybackError.toMessage(): String = when (this) {
    PlaybackError.Network -> "Connection lost — check your network or server."
    PlaybackError.Auth -> "Sign-in expired. Try signing out and back in."
    PlaybackError.NotFound -> "This track is no longer available on the server."
    PlaybackError.Unknown -> "Playback error."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    remainingMs: Long?,
    onDismiss: () -> Unit,
    onPick: (minutes: Int) -> Unit,
    onPickTrackEnd: () -> Unit,
    onCancelTimer: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = if (remainingMs != null) "Sleeping in ${formatTime(remainingMs)}" else "Sleep timer",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        listOf(15, 30, 45, 60).forEach { minutes ->
            ListItem(
                headlineContent = { Text("$minutes minutes") },
                modifier = Modifier.bouncyClickable(onClick = { onPick(minutes) }),
            )
        }
        ListItem(
            headlineContent = { Text("End of track") },
            modifier = Modifier.bouncyClickable(onClick = onPickTrackEnd),
        )
        if (remainingMs != null) {
            ListItem(
                headlineContent = {
                    Text("Cancel timer", color = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.bouncyClickable(onClick = onCancelTimer),
            )
        }
        Spacer(Modifier.size(16.dp))
    }
}
