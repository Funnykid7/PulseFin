package com.pulsefin.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val tracks: List<Song> = emptyList(),
    val error: String? = null,
)

class AlbumDetailViewModel(
    private val repository: MediaRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    var uiState by mutableStateOf(AlbumDetailUiState())
        private set

    private var loadedId: String? = null

    fun load(albumId: String) {
        if (loadedId == albumId) return
        loadedId = albumId
        uiState = AlbumDetailUiState(isLoading = true)
        viewModelScope.launch {
            uiState = when (val result = repository.songsForAlbum(albumId)) {
                is PulseResult.Success -> AlbumDetailUiState(isLoading = false, tracks = result.data)
                is PulseResult.Failure -> AlbumDetailUiState(isLoading = false, error = result.error.message ?: "Couldn't load album")
            }
        }
    }

    fun play(index: Int) = playbackController.play(uiState.tracks, index)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(albumId) { viewModel.load(albumId) }
    val state = viewModel.uiState
    val albumName = state.tracks.firstOrNull()?.albumName?.ifBlank { null } ?: "Album"
    val artUrl = state.tracks.firstOrNull()?.artworkUrl

    ArtworkTheme(artUrl) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                    title = { Text(albumName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )

                when {
                    state.isLoading -> Box1 { CircularProgressIndicator() }
                    state.error != null -> Box1 { Text(state.error, color = MaterialTheme.colorScheme.error) }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        item {
                            AlbumHeader(
                                artUrl = artUrl,
                                albumName = albumName,
                                artistName = state.tracks.firstOrNull()?.artistName.orEmpty(),
                                onPlay = { viewModel.play(0) },
                            )
                        }
                        itemsIndexed(state.tracks, key = { _, s -> s.id.value }) { index, song ->
                            TrackRow(
                                index = index + 1,
                                song = song,
                                isPlaying = song.id.value == currentMediaId,
                                onClick = { viewModel.play(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(artUrl: String?, albumName: String, artistName: String, onPlay: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(220.dp)
                .clip(MaterialTheme.shapes.extraLarge),
        )
        Spacer(Modifier.size(16.dp))
        Text(albumName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(artistName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Button(onClick = onPlay, shape = MaterialTheme.shapes.large) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Play")
        }
    }
}

@Composable
private fun TrackRow(index: Int, song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Box1(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}
