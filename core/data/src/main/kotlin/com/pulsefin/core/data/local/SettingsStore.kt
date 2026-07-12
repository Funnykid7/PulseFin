package com.pulsefin.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pulsefin.core.common.dispatchers.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "pulsefin_settings")

/** User-configurable app settings. Defaults preserve today's behavior. */
data class Settings(
    val darkTheme: Boolean = true,
    val dynamicColor: Boolean = true,
    val preferDownloadsOnCellular: Boolean = true,
)

/** Persists user-facing app settings in a plain (non-encrypted) DataStore — nothing here is sensitive. */
class SettingsStore(context: Context, private val dispatchers: AppDispatchers) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PREFER_DOWNLOADS_ON_CELLULAR = booleanPreferencesKey("prefer_downloads_on_cellular")
    }

    private val dataStore = context.dataStore

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            darkTheme = prefs[Keys.DARK_THEME] ?: defaults.darkTheme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            preferDownloadsOnCellular = prefs[Keys.PREFER_DOWNLOADS_ON_CELLULAR]
                ?: defaults.preferDownloadsOnCellular,
        )
    }

    suspend fun setDarkTheme(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setPreferDownloadsOnCellular(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[Keys.PREFER_DOWNLOADS_ON_CELLULAR] = enabled }
    }
}
