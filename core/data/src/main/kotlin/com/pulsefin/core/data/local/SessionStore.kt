package com.pulsefin.core.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pulsefin.core.common.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The persisted set-and-forget session for the single configured Jellyfin server. */
data class Session(
    val serverUrl: String,
    val accessToken: String,
    val userName: String,
    val userId: String?,
)

/**
 * Persists the authenticated session in Keystore-backed [EncryptedSharedPreferences] — the
 * access token never leaves the device, and is encrypted at rest (not just plaintext DataStore).
 */
class SessionStore(context: Context, private val dispatchers: AppDispatchers) {

    private object Keys {
        const val SERVER = "server_url"
        const val TOKEN = "access_token"
        const val USER_NAME = "user_name"
        const val USER_ID = "user_id"
    }

    // Deferred to first touch (rather than an eager field initializer) so the Keystore/crypto
    // init this triggers doesn't run the instant SessionStore is constructed — callers are
    // expected to force that first touch off the main thread (see PulseFinApp's warm-up).
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            context,
            "pulsefin_session_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session: Flow<Session?> = _session.asStateFlow()

    // Distinguishes "not yet read from disk" from "read, and there's no session" — both look
    // like a null _session, but callers (AuthRepositoryImpl.authState) need to tell them apart
    // to avoid a spurious LoggedOut flash before the disk read in init{} below completes.
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: Flow<Boolean> = _isLoaded.asStateFlow()

    /**
     * Synchronous snapshot of the current session, for callers that can't suspend (e.g. Media3's
     * ResolvingDataSource.Resolver, invoked on a download loader thread). Reflects the same state
     * as [session]; may briefly be null immediately after process start, before this class's own
     * init{} finishes its async read of EncryptedSharedPreferences.
     */
    val currentSession: Session? get() = _session.value

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    init {
        // Populate the initial value here, off the constructor call stack: reading it via a plain
        // field initializer (the previous approach) forces prefs' Keystore/crypto init and a disk
        // read synchronously on whichever thread constructs this Koin single — which can be the
        // main thread if Compose's first composition wins the race against PulseFinApp's IO
        // warm-up, defeating the "deferred" intent of prefs' own `by lazy`.
        scope.launch {
            _session.value = readSession()
            _isLoaded.value = true
        }
    }

    suspend fun save(session: Session) = withContext(dispatchers.io) {
        prefs.edit()
            .putString(Keys.SERVER, session.serverUrl)
            .putString(Keys.TOKEN, session.accessToken)
            .putString(Keys.USER_NAME, session.userName)
            .apply { session.userId?.let { putString(Keys.USER_ID, it) } ?: remove(Keys.USER_ID) }
            .apply()
        _session.value = session
        _isLoaded.value = true
    }

    suspend fun clear() = withContext(dispatchers.io) {
        prefs.edit().clear().apply()
        _session.value = null
        _isLoaded.value = true
    }

    private fun readSession(): Session? {
        val server = prefs.getString(Keys.SERVER, null)
        val token = prefs.getString(Keys.TOKEN, null)
        val user = prefs.getString(Keys.USER_NAME, null)
        return if (server != null && token != null && user != null) {
            Session(server, token, user, prefs.getString(Keys.USER_ID, null))
        } else {
            null
        }
    }
}
