package com.pulsefin.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ArtistsUiState(
    val isLoading: Boolean = true,
    val artists: List<Artist> = emptyList(),
    val error: String? = null,
)

class ArtistsViewModel(private val repository: MediaRepository) : ViewModel() {
    var uiState by mutableStateOf(ArtistsUiState())
        private set

    init { load() }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            uiState = when (val result = repository.artists()) {
                is PulseResult.Success -> ArtistsUiState(isLoading = false, artists = result.data)
                is PulseResult.Failure -> ArtistsUiState(isLoading = false, error = result.error.message ?: "Couldn't load artists")
            }
        }
    }
}

@Composable
fun ArtistsScreen(
    contentPadding: PaddingValues,
    onArtistClick: (String) -> Unit,
    viewModel: ArtistsViewModel = koinViewModel(),
) {
    val state = viewModel.uiState
    when {
        state.isLoading -> Centered(contentPadding) { CircularProgressIndicator() }
        state.error != null -> Centered(contentPadding) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        state.artists.isEmpty() -> Centered(contentPadding) {
            Text("No artists found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(state.artists, key = { it.id.value }) { artist ->
                ListItem(
                    modifier = Modifier.clickable { onArtistClick(artist.id.value) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape),
                        )
                    },
                    headlineContent = {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
