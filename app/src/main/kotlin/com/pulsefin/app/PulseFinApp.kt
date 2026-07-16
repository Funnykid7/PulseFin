package com.pulsefin.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pulsefin.app.di.appModule
import com.pulsefin.app.download.DownloadStateSync
import com.pulsefin.app.playback.PlaybackScrobbler
import com.pulsefin.core.data.di.dataModule
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.playback.di.playbackModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PulseFinApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val koinApp = startKoin {
            androidLogger()
            androidContext(this@PulseFinApp)
            modules(dataModule, playbackModule, appModule)
        }
        appScope.launch {
            // Resolve AuthRepository (and transitively SessionStore — EncryptedSharedPreferences
            // + a synchronous disk read) first and explicitly on IO, so that blocking init runs
            // off the main thread as early as possible instead of racing PulseFinRoot's first
            // composition (which also resolves AuthRepository, on the main thread) to trigger it.
            withContext(Dispatchers.IO) { koinApp.koin.get<AuthRepository>() }
            koinApp.koin.get<PlaybackScrobbler>().start()
            koinApp.koin.get<DownloadStateSync>().start()
        }
    }

    /**
     * Tuned image loader for smooth scrolling on low-end devices: RGB_565 halves bitmap memory,
     * caching is forced on (ignore server no-cache headers) so re-scrolls hit the cache, and a
     * generous memory + disk cache avoids re-decoding covers.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
}
