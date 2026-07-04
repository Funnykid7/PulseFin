package com.pulsefin.core.playback.di

import com.pulsefin.core.playback.controller.PlaybackController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for playback. The [com.pulsefin.core.playback.service.PlaybackService] is
 * Android-managed (declared in the manifest); [PlaybackController] connects the UI to it.
 */
val playbackModule: Module = module {
    single { PlaybackController(androidContext()) }
}
