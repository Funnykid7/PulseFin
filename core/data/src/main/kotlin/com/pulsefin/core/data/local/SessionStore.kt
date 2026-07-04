package com.pulsefin.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "pulsefin_session")

/** The persisted set-and-forget session for the single configured Jellyfin server. */
data class Session(
    val serverUrl: String,
    val accessToken: String,
    val userName: String,
    val userId: String?,
)

/**
 * Persists the authenticated session. Backed by DataStore; the access token never leaves
 * the device. (Wrapping the token in an encrypted store is a follow-up hardening step.)
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val SERVER = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("access_token")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ID = stringPreferencesKey("user_id")
    }

    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val server = prefs[Keys.SERVER]
        val token = prefs[Keys.TOKEN]
        val user = prefs[Keys.USER_NAME]
        if (server != null && token != null && user != null) {
            Session(server, token, user, prefs[Keys.USER_ID])
        } else {
            null
        }
    }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.SERVER] = session.serverUrl
            prefs[Keys.TOKEN] = session.accessToken
            prefs[Keys.USER_NAME] = session.userName
            val id = session.userId
            if (id != null) prefs[Keys.USER_ID] = id else prefs.remove(Keys.USER_ID)
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
