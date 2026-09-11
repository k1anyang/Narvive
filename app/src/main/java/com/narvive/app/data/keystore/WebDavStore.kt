package com.narvive.app.data.keystore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** WebDAV 密码加密存储（URL/用户名存 DataStore，密码单独加密） */
@Singleton
class WebDavStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "narvive_webdav",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            context.getSharedPreferences("narvive_webdav_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getPassword(): String? =
        try { prefs.getString(KEY_PASSWORD, null) } catch (_: Exception) { null }

    fun setPassword(password: String) {
        try { prefs.edit().putString(KEY_PASSWORD, password).apply() } catch (_: Exception) {}
    }

    fun clear() {
        try { prefs.edit().remove(KEY_PASSWORD).apply() } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_PASSWORD = "webdav_password"
    }
}
