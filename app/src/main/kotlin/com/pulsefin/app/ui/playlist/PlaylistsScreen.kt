package com.pulsefin.app.ui.playlist

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.RefreshBox
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.domain.model.DownloadState
import com.pulsefin.core.domain.model.Playlist
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class PlaylistsViewModel(
    private val repository: MediaRepository,
    downloadRepository: DownloadRepository,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasDownloads: StateFlow<Boolean> = downloadRepository.observeDownloads()
        .map { downloads -> downloads.values.any { it.state == DownloadState.COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var isRefreshing by mutableStateOf(false)
        private set

    private val _actionError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val actionError: SharedFlow<Unit> = _actionError.asSharedFlow()

    init { sync(force = false) }

    fun refresh() = sync(force = true)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (repository.createPlaylist(name) is PulseResult.Failure) _actionError.tryEmit(Unit)
        }
    }

    private fun sync(force: Boolean) {
        viewModelScope.launch {
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
fun PlaylistsScreen(
    contentPadding: PaddingValues,
    onPlaylistClick: (id: String) -> Unit,
    onDownloadsClick: () -> Unit,
    viewModel: PlaylistsViewModel = koinViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val hasDownloads by viewModel.hasDownloads.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val actionErrorMessage = stringResource(R.string.error_action_failed)
    LaunchedEffect(viewModel) {
        viewModel.actionError.collect { snackbarHostState.showSnackbar(actionErrorMessage) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    bottom = contentPadding.calculateBottomPadding() + 88.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasDownloads) {
                    item(key = "downloads", contentType = "downloads") {
                        DownloadsCard(onClick = onDownloadsClick, modifier = Modifier.animateItem())
                    }
                }
                if (playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.playlists_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(
                        playlists,
                        key = { it.id.value },
                        contentType = { "playlist" },
                    ) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id.value) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        val fabInteraction = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
                .pressScale(fabInteraction),
            interactionSource = fabInteraction,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlist_new))
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showCreateDialog) {
        NewPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun DownloadsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.bouncyClickable(pressedScale = 0.95f, onClick = onClick)) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(SquircleShape),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.playlist_downloads_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.bouncyClickable(pressedScale = 0.95f, onClick = onClick),
    ) {
        PlaylistArt(playlist)
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.playlist_song_count, playlist.songCount, playlist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Empty playlists get an icon placeholder, single-song playlists get one full-bleed cover, and
 * multi-song playlists get a real 2x2 collage of up to 4 distinct songs' art (not the server's
 * auto-generated composite, which only reflects the playlist's first song).
 */
@Composable
private fun PlaylistArt(playlist: Playlist) {
    val tileModifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(SquircleShape)
    val art = playlist.memberArtworkUrls
    when {
        playlist.songCount <= 0 -> PlaylistArtPlaceholder(tileModifier)
        art.size <= 1 -> {
            val url = art.firstOrNull() ?: playlist.artworkUrl
            if (url != null) {
                AsyncImage(
                    // 480, not 180: this fills a full 2-column grid tile, which at 2-3x density
                    // is well above 180px — a flat 180 request upscaled to fill the tile blurred.
                    model = sizedArtUrl(url, 480),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = tileModifier,
                )
            } else {
                PlaylistArtPlaceholder(tileModifier)
            }
        }
        else -> {
            Surface(modifier = tileModifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CollageQuadrant(art.getOrNull(0), Modifier.weight(1f).fillMaxHeight())
                        CollageQuadrant(art.getOrNull(1), Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CollageQuadrant(art.getOrNull(2), Modifier.weight(1f).fillMaxHeight())
                        CollageQuadrant(art.getOrNull(3), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageQuadrant(url: String?, modifier: Modifier) {
    if (url != null) {
        AsyncImage(
            // Each quadrant is ~1/4 of the tile, so 240 (roughly half of the full-tile 480) is
            // proportional rather than the same flat 180 used for a full-size cover.
            model = sizedArtUrl(url, 240),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier)
    }
}

@Composable
private fun PlaylistArtPlaceholder(modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}
