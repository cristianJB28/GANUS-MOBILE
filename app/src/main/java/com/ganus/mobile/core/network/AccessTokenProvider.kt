package com.ganus.mobile.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AccessTokenProvider {
    fun getAccessToken(): String?
}

data class AuthenticatedSession(
    val accessToken: String,
    val userId: String,
    val userName: String,
)

@Singleton
class PersistentAccessTokenProvider @Inject constructor(
    @ApplicationContext context: Context,
) : AccessTokenProvider {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Volatile
    private var session = readSession()

    override fun getAccessToken(): String? = session?.accessToken

    fun getSession(): AuthenticatedSession? = session

    @Synchronized
    fun saveSession(session: AuthenticatedSession) {
        require(session.accessToken.isNotBlank())
        require(session.userId.isNotBlank())
        require(session.userName.isNotBlank())
        check(
            preferences.edit()
                .putString(KEY_ACCESS_TOKEN, session.accessToken)
                .putString(KEY_USER_ID, session.userId)
                .putString(KEY_USER_NAME, session.userName)
                .commit(),
        ) { "No se pudo guardar la sesión de forma segura." }
        this.session = session
    }

    @Synchronized
    fun clearSession() {
        check(preferences.edit().clear().commit()) {
            "No se pudo cerrar la sesión de forma segura."
        }
        session = null
    }

    private fun readSession(): AuthenticatedSession? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
        val userId = preferences.getString(KEY_USER_ID, null)
        val userName = preferences.getString(KEY_USER_NAME, null)
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank() || userName.isNullOrBlank()) {
            return null
        }
        return AuthenticatedSession(accessToken, userId, userName)
    }

    private companion object {
        const val PREFERENCES_NAME = "ganus_authenticated_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
    }
}
