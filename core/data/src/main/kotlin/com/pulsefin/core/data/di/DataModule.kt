package com.pulsefin.core.data.di

import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.common.dispatchers.DefaultAppDispatchers
import com.pulsefin.core.data.jellyfin.JellyfinApiProvider
import com.pulsefin.core.data.jellyfin.JellyfinClientFactory
import com.pulsefin.core.data.local.AlbumDao
import com.pulsefin.core.data.local.ArtistDao
import com.pulsefin.core.data.local.PulseFinDatabase
import com.pulsefin.core.data.local.RecentSearchDao
import com.pulsefin.core.data.local.SessionStore
import com.pulsefin.core.data.local.SongDao
import com.pulsefin.core.data.repository.AuthRepositoryImpl
import com.pulsefin.core.data.repository.MediaRepositoryImpl
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.domain.repository.MediaRepository
import org.jellyfin.sdk.Jellyfin
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for the data layer. Consumers depend only on the domain interfaces
 * ([AuthRepository], [MediaRepository]); the Jellyfin SDK and Room stay internal here.
 */
val dataModule: Module = module {
    single<AppDispatchers> { DefaultAppDispatchers() }

    single { JellyfinClientFactory(androidContext(), clientVersion = "0.1.0") }
    single<Jellyfin> { get<JellyfinClientFactory>().create() }

    single { PulseFinDatabase.build(androidContext()) }
    single<SongDao> { get<PulseFinDatabase>().songDao() }
    single<AlbumDao> { get<PulseFinDatabase>().albumDao() }
    single<ArtistDao> { get<PulseFinDatabase>().artistDao() }
    single<RecentSearchDao> { get<PulseFinDatabase>().recentSearchDao() }

    single { SessionStore(androidContext()) }
    single { JellyfinApiProvider(get(), get()) }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<MediaRepository> { MediaRepositoryImpl(get(), get(), get(), get(), get(), get()) }
}
