package com.pulsefin.app.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulsefin.app.ui.components.DownloadStateIndicator
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.app.ui.components.sharedArtwork
import com.pulsefin.app.ui.theme.ArtworkTheme
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.DownloadState
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    var uiState by mutableStateOf(AlbumDetailUiState())
        private set

    private var loadedId: String? = null

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadRepository.observeDownloads()
        .map { downloads -> downloads.mapValues { it.value.state } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    fun toggleDownload(song: Song) = viewModelScope.launch {
        if (downloadStates.value[song.id.value] == DownloadState.COMPLETED) {
            downloadRepository.remove(song.id.value)
        } else {
            downloadRepository.download(song)
        }
    }

    fun downloadAllOrRemoveAll() = viewModelScope.launch {
        val tracks = uiState.tracks
        if (tracks.isNotEmpty() && tracks.all { downloadStates.value[it.id.value] == DownloadState.COMPLETED }) {
            downloadRepository.removeAll(tracks.map { it.id.value })
        } else {
            downloadRepository.downloadAll(tracks)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    initialArtUrl: String?,
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(albumId) { viewModel.load(albumId) }
    val state = viewModel.uiState
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val albumName = state.tracks.firstOrNull()?.albumName?.ifBlank { null } ?: "Album"
    // The nav-arg art renders the hero (and seeds Monet) immediately; tracks refine it later.
    val baseArt = state.tracks.firstOrNull()?.artworkUrl ?: initialArtUrl
    val artUrl = sizedArtUrl(baseArt, 512)
    val allDownloaded = state.tracks.isNotEmpty() &&
        state.tracks.all { downloadStates[it.id.value] == DownloadState.COMPLETED }

    ArtworkTheme(artUrl) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                    title = { Text(albumName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        val backInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.pressScale(backInteraction, pressedScale = 0.9f),
                            interactionSource = backInteraction,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )

                // The header stays composed while tracks load so the shared-element art morph
                // has something to land on; loading/error render as items beneath it.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    item(contentType = "header") {
                        AlbumHeader(
                            albumId = albumId,
                            artUrl = artUrl,
                            placeholderCacheKey = sizedArtUrl(baseArt, 180),
                            albumName = albumName,
                            artistName = state.tracks.firstOrNull()?.artistName.orEmpty(),
                            onPlay = { viewModel.play(0) },
                            allDownloaded = allDownloaded,
                            onDownloadAllOrRemoveAll = viewModel::downloadAllOrRemoveAll,
                        )
                    }
                    when {
                        state.isLoading -> item(contentType = "status") {
                            Box1(modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)) {
                                LoadingIndicator()
                            }
                        }
                        state.error != null -> item(contentType = "status") {
                            Box1(modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp)) {
                                Text(state.error, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> itemsIndexed(
                            state.tracks,
                            key = { _, s -> s.id.value },
                            contentType = { _, _ -> "track" },
                        ) { index, song ->
                            TrackRow(
                                index = index + 1,
                                song = song,
                                isPlaying = song.id.value == currentMediaId,
                                downloadState = downloadStates[song.id.value] ?: DownloadState.NONE,
                                onClick = { viewModel.play(index) },
                                onToggleDownload = { viewModel.toggleDownload(song) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    albumId: String,
    artUrl: String?,
    placeholderCacheKey: String?,
    albumName: String,
    artistName: String,
    onPlay: () -> Unit,
    allDownloaded: Boolean,
    onDownloadAllOrRemoveAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            // Show the grid tile's cached thumbnail while the large art loads, so the
            // shared-element morph lands on a real image instead of an empty box.
            model = ImageRequest.Builder(LocalContext.current)
                .data(artUrl)
                .placeholderMemoryCacheKey(placeholderCacheKey)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .sharedArtwork("album-art-$albumId")
                .size(220.dp)
                .clip(SquircleShape),
        )
        Spacer(Modifier.size(16.dp))
        Text(albumName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(artistName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val playInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onPlay,
                modifier = Modifier.pressScale(playInteraction, pressedScale = 0.92f),
                shape = MaterialTheme.shapes.large,
                interactionSource = playInteraction,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play")
            }
            val downloadInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = onDownloadAllOrRemoveAll,
                modifier = Modifier.pressScale(downloadInteraction, pressedScale = 0.92f),
                shape = MaterialTheme.shapes.large,
                interactionSource = downloadInteraction,
            ) {
                Icon(
                    if (allDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (allDownloaded) "Remove downloads" else "Download all")
            }
        }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    song: Song,
    isPlaying: Boolean,
    downloadState: DownloadState,
    onClick: () -> Unit,
    onToggleDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val titleColor by animateColorAsState(
        if (isPlaying) accent else MaterialTheme.colorScheme.onSurface,
        label = "trackTitleColor",
    )
    val indexColor by animateColorAsState(
        if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "trackIndexColor",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = indexColor,
        )
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DownloadStateIndicator(downloadState)
        IconButton(onClick = onToggleDownload) {
            Icon(
                if (downloadState == DownloadState.COMPLETED) Icons.Filled.DownloadDone else Icons.Filled.Download,
                contentDescription = if (downloadState == DownloadState.COMPLETED) "Remove download" else "Download",
            )
        }
    }
}

@Composable
private fun Box1(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) { content() }
}
