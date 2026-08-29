package com.pulsefin.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pulsefin.core.common.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "pulsefin_settings")

/** User-configurable app settings. Defaults preserve today's behavior. */
data class Settings(
    val darkTheme: Boolean = true,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

/** Persists user-facing app settings in a plain (non-encrypted) DataStore — nothing here is sensitive. */
class SettingsStore(context: Context, private val dispatchers: AppDispatchers) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    private val dataStore = context.dataStore

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            darkTheme = prefs[Keys.DARK_THEME] ?: defaults.darkTheme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
        )
    }

    // Synchronous last-known value, for seeding collectAsStateWithLifecycle's initialValue so a
    // config change (rotation) doesn't flash the Settings() default for a frame before the real
    // DataStore value re-arrives. Mirrors every emission of `settings` above; may briefly still be
    // the default immediately after process start, before the first DataStore read completes.
    @Volatile var currentSettings: Settings = Settings()
        private set

    private val mirrorScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    init {
        settings.onEach { currentSettings = it }.launchIn(mirrorScope)
    }

    suspend fun setDarkTheme(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }
}
