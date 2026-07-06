package com.pulsefin.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.RefreshBox
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.domain.model.Artist
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class ArtistsViewModel(private val repository: MediaRepository) : ViewModel() {
    val artists: StateFlow<List<Artist>> = repository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isRefreshing by mutableStateOf(false)
        private set

    init { sync(force = false) }

    fun refresh() = sync(force = true)

    private fun sync(force: Boolean) {
        viewModelScope.launch {
            // Spinner only for user-initiated pulls; the silent startup sync shouldn't flash it.
            if (force) isRefreshing = true
            repository.refreshLibrary(force = force)
            isRefreshing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    contentPadding: PaddingValues,
    onArtistClick: (String) -> Unit,
    viewModel: ArtistsViewModel = koinViewModel(),
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()

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
            if (artists.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                        Text("No artists yet — pull to refresh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(
                    artists,
                    key = { it.id.value },
                    contentType = { "artist" },
                ) { artist ->
                    MediaRow(
                        title = artist.name,
                        imageModel = sizedArtUrl(artist.artworkUrl, 180),
                        modifier = Modifier
                            .animateItem()
                            .bouncyClickable { onArtistClick(artist.id.value) },
                        imageSize = 52.dp,
                        imageShape = CircleShape,
                    )
                }
            }
        }
    }
}
