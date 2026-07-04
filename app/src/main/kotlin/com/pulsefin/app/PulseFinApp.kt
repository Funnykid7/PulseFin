package com.pulsefin.app

import android.app.Application
import com.pulsefin.app.di.appModule
import com.pulsefin.core.data.di.dataModule
import com.pulsefin.core.playback.di.playbackModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PulseFinApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PulseFinApp)
            modules(dataModule, playbackModule, appModule)
        }
    }
}
