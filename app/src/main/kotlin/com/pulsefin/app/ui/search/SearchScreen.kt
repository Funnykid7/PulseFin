package com.pulsefin.app.ui.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.designsystem.theme.SquircleShape
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.domain.repository.SearchResults
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: SearchResults = SearchResults(emptyList(), emptyList(), emptyList()),
)

class SearchViewModel(
    private val repository: MediaRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    var uiState by mutableStateOf(SearchUiState())
        private set

    private val queryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val debouncedInit = viewModelScope.launch {
        queryFlow.debounce(300).collectLatest { q -> runSearch(q) }
    }

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
        queryFlow.value = value
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            uiState = uiState.copy(isSearching = false, results = SearchResults(emptyList(), emptyList(), emptyList()))
            return
        }
        uiState = uiState.copy(isSearching = true)
        val result = repository.search(query)
        uiState = uiState.copy(
            isSearching = false,
            results = (result as? PulseResult.Success)?.data
                ?: SearchResults(emptyList(), emptyList(), emptyList()),
        )
    }

    fun playSong(index: Int) = playbackController.play(uiState.results.songs, index)
}

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAlbumClick: (id: String, artUrl: String?) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state = viewModel.uiState
    val results = state.results

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onBack,
                modifier = Modifier.pressScale(backInteraction, pressedScale = 0.9f),
                interactionSource = backInteraction,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            // Focus the field as the screen animates in so the keyboard is ready to type.
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search songs, albums, artists") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            if (results.songs.isNotEmpty()) {
                item { SectionHeader("Songs") }
                itemsIndexed(
                    results.songs,
                    key = { _, s -> "s-${s.id.value}" },
                    contentType = { _, _ -> "song" },
                ) { index, song ->
                    SongResultRow(
                        song = song,
                        onClick = { viewModel.playSong(index) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                itemsIndexed(
                    results.albums,
                    key = { _, a -> "al-${a.id.value}" },
                    contentType = { _, _ -> "result" },
                ) { _, album ->
                    ResultRow(
                        title = album.name,
                        subtitle = album.artistName,
                        artworkUrl = album.artworkUrl,
                        circle = false,
                        onClick = { onAlbumClick(album.id.value, album.artworkUrl) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists") }
                itemsIndexed(
                    results.artists,
                    key = { _, a -> "ar-${a.id.value}" },
                    contentType = { _, _ -> "result" },
                ) { _, artist ->
                    ResultRow(
                        title = artist.name,
                        subtitle = null,
                        artworkUrl = artist.artworkUrl,
                        circle = true,
                        onClick = { onArtistClick(artist.id.value) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SongResultRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ResultRow(title = song.title, subtitle = song.artistName, artworkUrl = song.artworkUrl, circle = false, onClick = onClick, modifier = modifier)

@Composable
private fun ResultRow(title: String, subtitle: String?, artworkUrl: String?, circle: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaRow(
        title = title,
        imageModel = sizedArtUrl(artworkUrl, 180),
        modifier = modifier.bouncyClickable(onClick = onClick),
        subtitle = subtitle,
        imageSize = 48.dp,
        imageShape = if (circle) CircleShape else SquircleShape,
        titleStyle = MaterialTheme.typography.bodyLarge,
    )
}
