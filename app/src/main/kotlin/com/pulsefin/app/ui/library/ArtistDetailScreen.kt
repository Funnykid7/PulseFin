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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

data class ArtistDetailUiState(
    val isLoading: Boolean = true,
    val albums: List<Album> = emptyList(),
    val error: String? = null,
)

class ArtistDetailViewModel(private val repository: MediaRepository) : ViewModel() {
    var uiState by mutableStateOf(ArtistDetailUiState())
        private set

    private var loadedId: String? = null

    fun load(artistId: String) {
        if (loadedId == artistId) return
        loadedId = artistId
        uiState = ArtistDetailUiState(isLoading = true)
        viewModelScope.launch {
            uiState = when (val result = repository.albumsForArtist(artistId)) {
                is PulseResult.Success -> ArtistDetailUiState(isLoading = false, albums = result.data)
                is PulseResult.Failure -> ArtistDetailUiState(isLoading = false, error = result.error.message ?: "Couldn't load artist")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    contentPadding: PaddingValues,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val state = viewModel.uiState
    val artistName = state.albums.firstOrNull()?.artistName?.ifBlank { null } ?: "Artist"

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                title = { Text(artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.albums, key = { it.id.value }) { album ->
                        Column(modifier = Modifier.clickable { onAlbumClick(album.id.value) }) {
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
