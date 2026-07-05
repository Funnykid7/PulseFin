package com.pulsefin.core.common.util

/**
 * Appends a server-side resize to a base Jellyfin image URL. Requesting small thumbnails for
 * lists (vs full-res covers) is the key to smooth scrolling on low-end devices. The stored base
 * URL has no query string, so appending is safe.
 */
fun sizedArtUrl(baseUrl: String?, sizePx: Int): String? =
    baseUrl?.let { "$it?maxWidth=$sizePx&maxHeight=$sizePx" }
