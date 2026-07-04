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
)
