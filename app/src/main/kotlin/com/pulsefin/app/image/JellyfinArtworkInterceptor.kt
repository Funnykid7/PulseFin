package com.pulsefin.app.image

import coil.intercept.Interceptor
import coil.request.ImageResult
import com.pulsefin.core.domain.repository.StreamUrlResolver

/**
 * MediaRepositoryImpl stores artwork URLs without an api_key so the token never lands in Room
 * (or, via the playback queue, on-disk queue state) — attach one here at load time instead, via
 * the same [StreamUrlResolver] abstraction :core:playback uses for audio streams (keeps :app off
 * the Jellyfin SDK directly).
 */
class JellyfinArtworkInterceptor(private val resolver: StreamUrlResolver) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String
        if (url == null || url.contains("api_key=", ignoreCase = true)) return chain.proceed(request)
        val authed = resolver.resolveArtworkUrl(url) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().data(authed).build())
    }
}
