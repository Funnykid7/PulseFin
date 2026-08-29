package com.pulsefin.core.playback.di

import android.net.Uri
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.StreamUrlResolver
import com.pulsefin.core.playback.controller.PlaybackController
import com.pulsefin.core.playback.download.DownloadRepositoryImpl
import com.pulsefin.core.playback.queue.QueueStateStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors

/**
 * Koin wiring for playback. The [com.pulsefin.core.playback.service.PlaybackService] is
 * Android-managed (declared in the manifest), so it pulls [QueueStateStore] via Koin's
 * `by inject()` rather than constructor injection; [PlaybackController] connects the UI to it.
 */
val playbackModule: Module = module {
    single { QueueStateStore(androidContext()) }
    single { PlaybackController(androidContext(), get(), get<StreamUrlResolver>()) }

    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    single {
        // NoOpCacheEvictor: downloads are explicit/opt-in and must persist until the user
        // removes them — an LRU evictor would silently delete "downloaded" content.
        SimpleCache(
            File(androidContext().filesDir, "media_cache"),
            NoOpCacheEvictor(),
            get<DatabaseProvider>(),
        )
    } bind Cache::class
    // Playback-side: READ-ONLY against the shared cache (see global constraint above) — ordinary
    // streaming must never write into this cache or disk usage grows unbounded.
    single<CacheDataSource.Factory> {
        CacheDataSource.Factory()
            .setCache(get())
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setCacheWriteDataSinkFactory(null)
    }
    single {
        // Persisted DownloadRequests (this DownloadManager's on-disk index) must never carry the
        // session's auth token — see DownloadRepositoryImpl.download(), which now resolves a
        // token-free URI to build them. Wrap the upstream fetcher so every actual HTTP request
        // (including resumes after reboot) gets a token attached fresh, in memory only.
        val resolver = get<StreamUrlResolver>()
        val resolvingUpstream = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { dataSpec ->
            dataSpec.withUri(Uri.parse(resolver.attachAuthToken(dataSpec.uri.toString())))
        }
        DownloadManager(
            androidContext(),
            get<DatabaseProvider>(),
            get<SimpleCache>(),
            resolvingUpstream,
            Executors.newFixedThreadPool(2),
        )
    }
    single<DownloadRepository> {
        DownloadRepositoryImpl(
            androidContext(),
            get(),
            get<StreamUrlResolver>(),
            get<AppDispatchers>(),
        )
    }
}
