package com.pulsefin.core.data.jellyfin

import com.pulsefin.core.domain.repository.StreamUrlResolver
import org.jellyfin.sdk.api.client.extensions.audioApi
import java.util.UUID

class StreamUrlResolverImpl(private val apiProvider: JellyfinApiProvider) : StreamUrlResolver {
    override suspend fun resolveStreamUrl(songId: String): String? {
        val api = apiProvider.api() ?: return null
        val url = api.audioApi.getAudioStreamUrl(itemId = UUID.fromString(songId), static = true)
        return ensureApiKey(url, api.accessToken)
    }
}
