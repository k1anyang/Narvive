package com.narvive.app.data.keystore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密存储 API Key（EncryptedSharedPreferences）。
 * 使用 MasterKey.Builder（替代已废弃的 MasterKeys）；
 * Keystore 异常时 fallback 到普通 SharedPreferences（部分设备/自定义 ROM 不支持）。
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            // 1.1.0 新签名：create(context, fileName, masterKey, ...)
            EncryptedSharedPreferences.create(
                context,
                "narvive_api_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Keystore 不可用（部分设备/ROM）→ 降级为明文（优于崩溃）
            context.getSharedPreferences("narvive_api_keys_fallback", Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(providerId: String): String? =
        try { prefs.getString(providerId, null) } catch (_: Exception) { null }

    fun setApiKey(providerId: String, key: String) {
        try { prefs.edit().putString(providerId, key).apply() } catch (_: Exception) {}
    }

    fun removeApiKey(providerId: String) {
        try { prefs.edit().remove(providerId).apply() } catch (_: Exception) {}
    }

    fun getAllProviderIds(): Set<String> =
        try { prefs.all.keys } catch (_: Exception) { emptySet() }
}
