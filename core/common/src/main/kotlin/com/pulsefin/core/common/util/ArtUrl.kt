package com.pulsefin.core.common.util

/**
 * Appends a server-side resize to a base Jellyfin image URL. Requesting small thumbnails for
 * lists (vs full-res covers) is the key to smooth scrolling on low-end devices.
 */
fun sizedArtUrl(baseUrl: String?, sizePx: Int): String? = baseUrl?.takeIf { it.isNotEmpty() }?.let {
    val separator = if (it.contains('?')) '&' else '?'
    "$it${separator}maxWidth=$sizePx&maxHeight=$sizePx"
}
