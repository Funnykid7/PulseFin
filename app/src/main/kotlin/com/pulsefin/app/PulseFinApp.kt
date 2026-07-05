package com.pulsefin.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pulsefin.app.di.appModule
import com.pulsefin.core.data.di.dataModule
import com.pulsefin.core.playback.di.playbackModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PulseFinApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PulseFinApp)
            modules(dataModule, playbackModule, appModule)
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
