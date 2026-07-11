package com.pulsefin.core.data.jellyfin

/** Direct-play URLs must carry auth for ExoPlayer; append the token if the SDK didn't. */
internal fun ensureApiKey(url: String, token: String?): String {
    if (token.isNullOrBlank() || url.contains("api_key=", ignoreCase = true)) return url
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}api_key=$token"
}
