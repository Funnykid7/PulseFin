package com.pulsefin.app.ui.playlist

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.domain.model.Playlist
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class PlaylistDetailUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel(
    private val repository: MediaRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    var uiState by mutableStateOf(PlaylistDetailUiState())
        private set

    private val _playlistId = MutableStateFlow<String?>(null)
    val playlist: StateFlow<Playlist?> = _playlistId.filterNotNull().flatMapLatest { id ->
        repository.observePlaylists().map { list -> list.firstOrNull { it.id.value == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(playlistId: String) {
        if (_playlistId.value == playlistId) return
        _playlistId.value = playlistId
        reloadSongs()
    }

    fun play(index: Int) = playbackController.play(uiState.songs, index)

    fun removeSong(entryId: String) {
        val id = _playlistId.value ?: return
        viewModelScope.launch {
            repository.removeFromPlaylist(id, listOf(entryId))
            reloadSongs()
        }
    }

    fun rename(name: String) {
        val id = _playlistId.value ?: return
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _playlistId.value ?: return
        viewModelScope.launch {
            repository.deletePlaylist(id)
            onDeleted()
        }
    }

    private fun reloadSongs() {
        val id = _playlistId.value ?: return
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            uiState = when (val result = repository.songsForPlaylist(id)) {
                is PulseResult.Success -> PlaylistDetailUiState(isLoading = false, songs = result.data)
                is PulseResult.Failure -> PlaylistDetailUiState(
                    isLoading = false,
                    error = result.error.message ?: "Couldn't load playlist",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    val state = viewModel.uiState
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                title = { Text(playlist?.name ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { showMenu = false; showRenameDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { showMenu = false; showDeleteDialog = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item(contentType = "header") {
                    PlaylistDetailHeader(
                        name = playlist?.name.orEmpty(),
                        songCount = playlist?.songCount ?: state.songs.size,
                        onPlay = { viewModel.play(0) },
                    )
                }
                when {
                    state.isLoading -> item(contentType = "status") {
                        Box(modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    state.error != null -> item(contentType = "status") {
                        Box(modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    state.songs.isEmpty() -> item(contentType = "status") {
                        Box(modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text("No songs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> itemsIndexed(
                        state.songs,
                        key = { _, s -> s.playlistItemId ?: s.id.value },
                        contentType = { _, _ -> "song" },
                    ) { index, song ->
                        MediaRow(
                            title = song.title,
                            imageModel = sizedArtUrl(song.artworkUrl, 180),
                            modifier = Modifier
                                .animateItem()
                                .bouncyClickable(onClick = { viewModel.play(index) }),
                            subtitle = song.artistName,
                            titleColor = if (song.id.value == currentMediaId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            trailing = {
                                val entryId = song.playlistItemId
                                if (entryId != null) {
                                    IconButton(onClick = { viewModel.removeSong(entryId) }) {
                                        Icon(
                                            Icons.Filled.RemoveCircleOutline,
                                            contentDescription = "Remove from playlist",
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            initialName = playlist?.name.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onRename = { name -> viewModel.rename(name); showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist?") },
            text = { Text("This removes \"${playlist?.name.orEmpty()}\" from the server. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RenamePlaylistDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PlaylistDetailHeader(name: String, songCount: Int, onPlay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "$songCount songs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
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
    }
}
