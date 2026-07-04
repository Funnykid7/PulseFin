package com.pulsefin.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsefin.core.domain.model.Song

/**
 * Home = a flat, tappable list of the server's songs, with the now-playing row highlighted.
 * The top bar and mini-player live in the surrounding scaffold; this screen owns the list.
 * Becomes the PixelPlay-style "Your Mix" feed in a later increment.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    currentMediaId: String?,
    viewModel: HomeViewModel = org.koin.androidx.compose.koinViewModel(),
) {
    val state = viewModel.uiState

    when {
        state.isLoading -> Centered(contentPadding) { CircularProgressIndicator() }
        state.error != null -> Centered(contentPadding) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        state.songs.isEmpty() -> Centered(contentPadding) {
            Text("No songs found on your server.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(state.songs, key = { _, song -> song.id.value }) { index, song ->
                SongRow(
                    song = song,
                    isPlaying = song.id.value == currentMediaId,
                    onClick = { viewModel.onSongClick(index) },
                )
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        },
        headlineContent = {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun Centered(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
