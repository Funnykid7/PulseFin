package com.pulsefin.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.RefreshBox
import com.pulsefin.app.ui.components.SongOverflowMenu
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.app.ui.playlist.AddToPlaylistSheet
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.domain.model.Song
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Songs tab: the flat song list, served from Room (instant/offline) and refreshable by
 * pull-to-refresh. The now-playing row is highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    currentMediaId: String?,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var addToPlaylistSongId by remember { mutableStateOf<String?>(null) }

    RefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::refresh,
        topPadding = contentPadding.calculateTopPadding(),
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (songs.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                        Text(
                            "No songs yet — pull to refresh.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    songs,
                    key = { _, song -> song.id.value },
                    contentType = { _, _ -> "song" },
                ) { index, song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id.value == currentMediaId,
                        onClick = { viewModel.onSongClick(index) },
                        onPlayNext = { viewModel.playNext(song) },
                        onAddToQueue = { viewModel.addToQueue(song) },
                        onAddToPlaylist = { addToPlaylistSongId = song.id.value },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        // Only worth surfacing once you've actually scrolled a few screens deep.
        AnimatedVisibility(
            visible = listState.firstVisibleItemIndex > 3,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            val interaction = remember { MutableInteractionSource() }
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.pressScale(interaction),
                interactionSource = interaction,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }
    }

    val songIdForSheet = addToPlaylistSongId
    if (songIdForSheet != null) {
        AddToPlaylistSheet(songId = songIdForSheet, onDismiss = { addToPlaylistSongId = null })
    }
}

@Composable
private fun SongRow(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val titleColor by animateColorAsState(
        if (isPlaying) accent else MaterialTheme.colorScheme.onSurface,
        label = "songTitleColor",
    )
    val trailingColor by animateColorAsState(
        if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "songTrailingColor",
    )
    MediaRow(
        title = song.title,
        imageModel = sizedArtUrl(song.artworkUrl, 180),
        modifier = modifier.bouncyClickable(onClick = onClick),
        subtitle = song.artistName,
        titleColor = titleColor,
        titleWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
        trailingText = formatDuration(song.durationMs),
        trailingColor = trailingColor,
        trailing = {
            SongOverflowMenu(
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
            )
        },
    )
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
