package com.pulsefin.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.RefreshBox
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.sharedArtwork
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class AlbumsViewModel(private val repository: MediaRepository) : ViewModel() {
    val albums: StateFlow<List<Album>> = repository.observeAlbums()
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
            // Only this call's own isRefreshing=true should be cleared by it — otherwise a
            // concurrently-running non-forced sync can clear the spinner for a still-in-progress
            // forced (pull-to-refresh) one.
            if (force) isRefreshing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    contentPadding: PaddingValues,
    onAlbumClick: (id: String, artUrl: String?) -> Unit,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    RefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::refresh,
        topPadding = contentPadding.calculateTopPadding(),
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
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
            if (albums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.albums_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(
                    albums,
                    key = { it.id.value },
                    contentType = { "album" },
                ) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album.id.value, album.artworkUrl) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.bouncyClickable(pressedScale = 0.95f, onClick = onClick),
    ) {
        AsyncImage(
            // 480, not 180: this fills a full 2-column grid tile, which at 2-3x density is well
            // above 180px — a flat 180 request upscaled to fill the tile looked blurred.
            model = sizedArtUrl(album.artworkUrl, 480),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .sharedArtwork("album-art-${album.id.value}")
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SquircleShape),
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
