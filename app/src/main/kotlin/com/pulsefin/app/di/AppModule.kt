package com.pulsefin.app.di

import com.pulsefin.app.download.DownloadStateSync
import com.pulsefin.app.playback.PlaybackScrobbler
import com.pulsefin.app.ui.home.HomeViewModel
import com.pulsefin.app.ui.home.YourMixViewModel
import com.pulsefin.app.ui.library.AlbumDetailViewModel
import com.pulsefin.app.ui.library.AlbumsViewModel
import com.pulsefin.app.ui.library.ArtistDetailViewModel
import com.pulsefin.app.ui.library.ArtistsViewModel
import com.pulsefin.app.ui.login.LoginViewModel
import com.pulsefin.app.ui.playlist.DownloadsViewModel
import com.pulsefin.app.ui.playlist.PlaylistDetailViewModel
import com.pulsefin.app.ui.playlist.PlaylistsViewModel
import com.pulsefin.app.ui.search.SearchViewModel
import com.pulsefin.app.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-level Koin wiring (ViewModels, navigation-scoped dependencies). Feature ViewModels
 * are registered here as screens are built out.
 */
val appModule: Module = module {
    single { PlaybackScrobbler(get(), get()) }
    single { DownloadStateSync(get(), get(), get()) }
    viewModelOf(::LoginViewModel)
    viewModelOf(::YourMixViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::AlbumsViewModel)
    viewModelOf(::ArtistsViewModel)
    viewModelOf(::AlbumDetailViewModel)
    viewModelOf(::ArtistDetailViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::PlaylistsViewModel)
    viewModelOf(::PlaylistDetailViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::SettingsViewModel)
}
