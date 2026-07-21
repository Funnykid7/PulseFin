package com.pulsefin.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.RefreshBox
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.designsystem.theme.blobShapes
import com.pulsefin.core.domain.model.Album
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class YourMixViewModel(
    private val repository: MediaRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val songs: StateFlow<List<Song>> = repository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> =
        combine(repository.observeSongs(), repository.observeFavoriteIds()) { all, favIds ->
            all.filter { it.id.value in favIds }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Album art for the hero blob cluster; falls back to song art before albums load. */
    val songsFlow: StateFlow<List<Song>> get() = songs

    var recentlyAdded by mutableStateOf<List<Album>>(emptyList())
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    init { sync(force = false) }

    fun refresh() = sync(force = true)

    private fun sync(force: Boolean) {
        viewModelScope.launch {
            if (force) isRefreshing = true
            repository.refreshLibrary(force = force)
            recentlyAdded = when (val r = repository.recentlyAdded(20)) {
                is PulseResult.Success -> r.data
                is PulseResult.Failure -> recentlyAdded
            }
            // Only this call's own isRefreshing=true should be cleared by it — otherwise a
            // concurrently-running non-forced sync can clear the spinner for a still-in-progress
            // forced (pull-to-refresh) one.
            if (force) isRefreshing = false
        }
    }

    fun onShufflePlay() {
        val all = songs.value
        if (all.isNotEmpty()) viewModelScope.launch { playbackController.play(all.shuffled(), 0) }
    }

    // Resolve by id against the current list at click time rather than trusting a captured list
    // index — a concurrent library sync that reorders songs between render and click would
    // otherwise play the wrong track.
    fun onFavoriteClick(song: Song) = viewModelScope.launch {
        val index = favoriteSongs.value.indexOfFirst { it.id.value == song.id.value }
        if (index >= 0) playbackController.play(favoriteSongs.value, index)
    }
}

/**
 * Home "Your Mix" feed: a shuffle-all hero with an organic blob art cluster, a Recently Added
 * shelf, and the Liked Songs list. Everything is served from Room (instant/offline) with a
 * pull-to-refresh that also fetches the newest albums from the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YourMixScreen(
    contentPadding: PaddingValues,
    currentMediaId: String?,
    onAlbumClick: (id: String, artUrl: String?) -> Unit,
    viewModel: YourMixViewModel = koinViewModel(),
) {
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val allSongs by viewModel.songsFlow.collectAsStateWithLifecycle()
    val recent = viewModel.recentlyAdded

    // Prefer newest-album art for the cluster; fall back to whatever songs we have.
    val clusterArt = (recent.mapNotNull { it.artworkUrl } + allSongs.mapNotNull { it.artworkUrl })
        .distinct()
        .take(3)

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
            item(key = "hero") {
                YourMixHero(
                    clusterArt = clusterArt,
                    onShufflePlay = viewModel::onShufflePlay,
                )
            }

            if (recent.isNotEmpty()) {
                item(key = "recent-header") { SectionHeader(stringResource(R.string.section_recently_added)) }
                item(key = "recent-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recent, key = { it.id.value }, contentType = { "album" }) { album ->
                            RecentAlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album.id.value, album.artworkUrl) },
                            )
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item(key = "liked-header") { SectionHeader(stringResource(R.string.section_liked_songs)) }
                items(
                    favorites,
                    key = { song -> "fav-${song.id.value}" },
                    contentType = { "song" },
                ) { song ->
                    MediaRow(
                        title = song.title,
                        imageModel = sizedArtUrl(song.artworkUrl, 180),
                        modifier = Modifier
                            .bouncyClickable(onClick = { viewModel.onFavoriteClick(song) })
                            .animateItem(),
                        subtitle = song.artistName,
                        titleColor = if (song.id.value == currentMediaId)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        titleWeight = if (song.id.value == currentMediaId) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun YourMixHero(clusterArt: List<String>, onShufflePlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_your_mix),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_todays_mix),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            val playInteraction = remember { MutableInteractionSource() }
            FilledIconButton(
                onClick = onShufflePlay,
                modifier = Modifier
                    .size(56.dp)
                    .pressScale(playInteraction),
                colors = IconButtonDefaults.filledIconButtonColors(),
                interactionSource = playInteraction,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.home_shuffle_all),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        BlobCluster(art = clusterArt)
    }
}

/** A small overlapping cluster of album thumbnails, each clipped to a different blob shape. */
@Composable
private fun BlobCluster(art: List<String>) {
    if (art.isEmpty()) return
    val shapes = blobShapes()
    Box(modifier = Modifier.size(148.dp)) {
        // Back-to-front so the largest blob anchors the cluster and the others peek around it.
        art.getOrNull(1)?.let {
            AsyncImage(
                model = sizedArtUrl(it, 180),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(72.dp)
                    .clip(shapes[1]),
            )
        }
        art.getOrNull(2)?.let {
            AsyncImage(
                model = sizedArtUrl(it, 180),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 4.dp)
                    .size(60.dp)
                    .clip(shapes[2]),
            )
        }
        art.getOrNull(0)?.let {
            AsyncImage(
                model = sizedArtUrl(it, 180),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(92.dp)
                    .clip(shapes[0]),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun RecentAlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .bouncyClickable(pressedScale = 0.95f, onClick = onClick),
    ) {
        AsyncImage(
            model = sizedArtUrl(album.artworkUrl, 180),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
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
