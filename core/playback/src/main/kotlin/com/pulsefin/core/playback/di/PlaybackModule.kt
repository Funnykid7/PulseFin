package com.pulsefin.core.playback.di

import com.pulsefin.core.playback.controller.PlaybackController
import com.pulsefin.core.playback.queue.QueueStateStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for playback. The [com.pulsefin.core.playback.service.PlaybackService] is
 * Android-managed (declared in the manifest), so it pulls [QueueStateStore] via Koin's
 * `by inject()` rather than constructor injection; [PlaybackController] connects the UI to it.
 */
val playbackModule: Module = module {
    single { QueueStateStore(androidContext()) }
    single { PlaybackController(androidContext(), get()) }
}
