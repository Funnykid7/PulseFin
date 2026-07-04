package com.pulsefin.core.data.jellyfin

import android.content.Context
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

/**
 * Single place that constructs the official Jellyfin Kotlin SDK client. Everything else in
 * the data layer depends on repository interfaces, not on this type, so the SDK stays
 * swappable and the app module remains decoupled from the Jellyfin wire format.
 */
class JellyfinClientFactory(
    private val context: Context,
    private val clientVersion: String,
) {
    fun create(): Jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "PulseFin", version = clientVersion)
        this.context = this@JellyfinClientFactory.context
    }
}
