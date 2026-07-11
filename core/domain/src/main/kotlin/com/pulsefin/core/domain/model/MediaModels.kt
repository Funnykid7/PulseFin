package com.pulsefin.core.domain.model

/**
 * Provider-agnostic media models. The data layer maps Jellyfin SDK DTOs onto these,
 * keeping the rest of the app decoupled from the Jellyfin wire format.
 */

@JvmInline
value class MediaId(val value: String)

data class Artist(
    val id: MediaId,
    val name: String,
    val artworkUrl: String? = null,
)

data class Album(
    val id: MediaId,
    val name: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val year: Int? = null,
)

data class Song(
    val id: MediaId,
    val title: String,
    val albumName: String,
    val artistName: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    /** Direct-play stream URL resolved from the Jellyfin server. */
    val streamUrl: String? = null,
    val isFavorite: Boolean = false,
    /**
     * The playlist-entry ID for this song, only populated when it came from
     * [com.pulsefin.core.domain.repository.MediaRepository.songsForPlaylist] — distinct from
     * [id], and required by Jellyfin's remove/reorder playlist-item calls.
     */
    val playlistItemId: String? = null,
)

data class Playlist(
    val id: MediaId,
    val name: String,
    val artworkUrl: String? = null,
    val songCount: Int = 0,
    /** Art of up to the first 4 songs, for a per-song collage tile — distinct from [artworkUrl]. */
    val memberArtworkUrls: List<String> = emptyList(),
)

/** One line of lyrics; [startMs] is null for unsynced (plain-text) lyrics. */
data class LyricLine(
    val startMs: Long?,
    val text: String,
)

data class Lyrics(val lines: List<LyricLine>) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** True when at least one line is time-stamped, so a synced view is meaningful. */
    val isSynced: Boolean get() = lines.any { it.startMs != null }
}
