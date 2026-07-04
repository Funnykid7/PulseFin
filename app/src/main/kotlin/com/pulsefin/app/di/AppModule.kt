package com.pulsefin.app.di

import com.pulsefin.app.ui.home.HomeViewModel
import com.pulsefin.app.ui.login.LoginViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-level Koin wiring (ViewModels, navigation-scoped dependencies). Feature ViewModels
 * are registered here as screens are built out.
 */
val appModule: Module = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
}
