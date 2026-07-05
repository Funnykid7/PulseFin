package com.pulsefin.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class AlbumsUiState(
    val isLoading: Boolean = true,
    val albums: List<Album> = emptyList(),
    val error: String? = null,
)

class AlbumsViewModel(private val repository: MediaRepository) : ViewModel() {
    var uiState by mutableStateOf(AlbumsUiState())
        private set

    init { load() }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            uiState = when (val result = repository.albums()) {
                is PulseResult.Success -> AlbumsUiState(isLoading = false, albums = result.data)
                is PulseResult.Failure -> AlbumsUiState(isLoading = false, error = result.error.message ?: "Couldn't load albums")
            }
        }
    }
}

@Composable
fun AlbumsScreen(
    contentPadding: PaddingValues,
    onAlbumClick: (String) -> Unit,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val state = viewModel.uiState
    when {
        state.isLoading -> Centered(contentPadding) { CircularProgressIndicator() }
        state.error != null -> Centered(contentPadding) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        state.albums.isEmpty() -> Centered(contentPadding) {
            Text("No albums found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.albums, key = { it.id.value }) { album ->
                AlbumCard(album = album, onClick = { onAlbumClick(album.id.value) })
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium),
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun Centered(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}
