package com.pulsefin.app.ui.playlist

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.DownloadStateIndicator
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.data.local.DownloadDao
import com.pulsefin.core.domain.model.DownloadState
import com.pulsefin.core.domain.model.MediaId
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** A row on the Downloads screen: a song paired with where its download currently stands. */
data class DownloadEntry(val song: Song, val state: DownloadState)

/**
 * The "Downloads" pseudo-playlist: not a real server playlist (no [MediaRepository.observePlaylists]
 * entry, no playlist id) — just a view over this device's downloads, in every state but [DownloadState.NONE]
 * (queued/downloading/completed/failed all surface here, via [DownloadStateIndicator] per row — this
 * is the one screen dedicated to browsing them, so a completed-only filter would otherwise hide
 * everything currently in progress or that failed). Sourced from [DownloadDao] rather than
 * [MediaRepository.observeSongs] so a song stays listed here even if it's later removed from the
 * server library while still downloaded on-device.
 */
class DownloadsViewModel(
    private val downloadDao: DownloadDao,
    private val mediaRepository: MediaRepository,
    private val playbackController: PlaybackController,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val entries: StateFlow<List<DownloadEntry>> = combine(
        downloadDao.observeAll(),
        mediaRepository.observeSongs(),
    ) { downloads, librarySongs ->
        val songsById = librarySongs.associateBy { it.id.value }
        downloads
            .filter { it.state != DownloadState.NONE.name }
            .map { entity ->
                // Prefer the live library copy (duration, album); fall back to the download's own
                // denormalized metadata for a song that's since been removed from the server.
                val song = songsById[entity.songId] ?: Song(
                    id = MediaId(entity.songId),
                    title = entity.title,
                    albumName = "",
                    artistName = entity.artistName,
                    durationMs = 0L,
                    artworkUrl = entity.artworkUrl,
                )
                DownloadEntry(song, DownloadState.valueOf(entity.state))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playAll() = viewModelScope.launch {
        val songs = entries.value.map { it.song }
        if (songs.isNotEmpty()) playbackController.play(songs, 0)
    }

    // Resolve by id against the current list at click time rather than a captured index.
    fun play(song: Song) = viewModelScope.launch {
        val songs = entries.value.map { it.song }
        val index = songs.indexOfFirst { it.id.value == song.id.value }
        if (index >= 0) playbackController.play(songs, index)
    }

    fun removeDownload(songId: String) = viewModelScope.launch { downloadRepository.remove(songId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                title = { Text(stringResource(R.string.playlist_downloads_title)) },
                navigationIcon = {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.pressScale(backInteraction, pressedScale = 0.9f),
                        interactionSource = backInteraction,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item(contentType = "header") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.playlist_downloads_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            pluralStringResource(R.plurals.playlist_song_count, entries.size, entries.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(16.dp))
                        val playInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = viewModel::playAll,
                            modifier = Modifier.pressScale(playInteraction, pressedScale = 0.92f),
                            shape = MaterialTheme.shapes.large,
                            interactionSource = playInteraction,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_play))
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item(contentType = "status") {
                        Box(modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.playlist_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(
                        entries,
                        key = { it.song.id.value },
                        contentType = { "song" },
                    ) { entry ->
                        val song = entry.song
                        MediaRow(
                            title = song.title,
                            imageModel = sizedArtUrl(song.artworkUrl, 180),
                            modifier = Modifier.bouncyClickable(onClick = { viewModel.play(song) }),
                            subtitle = song.artistName,
                            titleColor = if (song.id.value == currentMediaId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DownloadStateIndicator(entry.state)
                                    IconButton(onClick = { viewModel.removeDownload(song.id.value) }) {
                                        Icon(
                                            Icons.Filled.RemoveCircleOutline,
                                            contentDescription = stringResource(R.string.action_remove_download),
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
}
