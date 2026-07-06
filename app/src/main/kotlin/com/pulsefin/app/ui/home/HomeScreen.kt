package com.pulsefin.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.RefreshBox
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.domain.model.Song
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

    RefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::refresh,
        topPadding = contentPadding.calculateTopPadding(),
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    )
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
