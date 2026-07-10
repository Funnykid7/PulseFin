package com.pulsefin.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import android.view.HapticFeedbackConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Lyrics
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import org.koin.compose.koinInject

private enum class LyricsMode { Synced, Static }

/**
 * Lyrics for the current track. Timed lyrics highlight the active line and auto-scroll; plain
 * lyrics render statically. Falls back to a graceful empty state when the server has none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    playbackController: PlaybackController = koinInject(),
    repository: MediaRepository = koinInject(),
) {
    val state by playbackController.state.collectAsStateWithLifecycle()
    val positionMs by playbackController.positionMs.collectAsStateWithLifecycle()
    val mediaId = state.currentMediaId

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(mediaId) {
        loading = true
        lyrics = mediaId?.let { id ->
            when (val r = repository.lyrics(id)) {
                is PulseResult.Success -> r.data
                is PulseResult.Failure -> Lyrics(emptyList())
            }
        } ?: Lyrics(emptyList())
        loading = false
    }

    val current = lyrics
    var mode by remember(mediaId) { mutableStateOf(LyricsMode.Synced) }

    ArtworkTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                LyricsTopBar(onBack = onBack, title = state.title.orEmpty())

                val synced = current?.isSynced == true
                if (synced) {
                    val view = LocalView.current
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        SegmentedButton(
                            selected = mode == LyricsMode.Synced,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                mode = LyricsMode.Synced
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Synced") }
                        SegmentedButton(
                            selected = mode == LyricsMode.Static,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                mode = LyricsMode.Static
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Static") }
                    }
                }

                when {
                    loading -> EmptyState("Loading lyrics…")
                    current == null || current.isEmpty -> EmptyState("No lyrics available")
                    else -> LyricLines(
                        lyrics = current,
                        positionMs = positionMs,
                        highlightActive = synced && mode == LyricsMode.Synced,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsTopBar(onBack: () -> Unit, title: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = onBack,
            modifier = Modifier.pressScale(backInteraction, pressedScale = 0.9f),
            interactionSource = backInteraction,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title.ifBlank { "Lyrics" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LyricLines(lyrics: Lyrics, positionMs: Long, highlightActive: Boolean) {
    val activeIndex = if (highlightActive) {
        lyrics.lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }.coerceAtLeast(0)
    } else -1

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == activeIndex
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
