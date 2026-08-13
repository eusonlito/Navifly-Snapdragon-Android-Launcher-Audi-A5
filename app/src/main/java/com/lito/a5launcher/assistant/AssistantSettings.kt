package com.lito.a5launcher.assistant

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AssistantSettings(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val secrets by lazy { KeystoreSecretStore(preferences) }

    var provider: AssistantProvider
        get() = AssistantProvider.entries.firstOrNull {
            it.name == preferences.getString(KEY_PROVIDER, AssistantProvider.DISABLED.name)
        } ?: AssistantProvider.DISABLED
        set(value) {
            preferences.edit { putString(KEY_PROVIDER, value.name) }
        }

    var errorLoggingEnabled: Boolean
        get() = preferences.getBoolean(KEY_ERROR_LOGGING, false)
        set(value) {
            preferences.edit { putBoolean(KEY_ERROR_LOGGING, value) }
        }

    fun apiKey(provider: AssistantProvider): String? = secrets.read(provider.secretKey)

    fun apiKey(kind: AssistantCredentialKind): String? = secrets.read(kind.secretKey)

    fun saveApiKey(kind: AssistantCredentialKind, value: String) {
        saveSecret(kind.secretKey, value)
    }

    fun placesApiKey(): String? = secrets.read(PLACES_API_KEY)

    fun deleteApiKey(kind: AssistantCredentialKind) {
        secrets.delete(kind.secretKey)
    }

    fun hasApiKey(provider: AssistantProvider): Boolean = !apiKey(provider).isNullOrBlank()

    private fun saveSecret(key: String, value: String) {
        if (value.isBlank()) secrets.delete(key) else secrets.write(key, value.trim())
    }

    private val AssistantProvider.secretKey: String get() = "api_key_${name.lowercase()}"
    private val AssistantCredentialKind.secretKey: String get() = when (this) {
        AssistantCredentialKind.OPENAI -> AssistantProvider.OPENAI.secretKey
        AssistantCredentialKind.GEMINI -> AssistantProvider.GEMINI.secretKey
        AssistantCredentialKind.PLACES -> PLACES_API_KEY
    }

    private companion object {
        const val PREFERENCES_NAME = "assistant_settings"
        const val KEY_PROVIDER = "provider"
        const val KEY_ERROR_LOGGING = "error_logging"
        const val PLACES_API_KEY = "api_key_google_places"
    }
}

private class KeystoreSecretStore(private val preferences: SharedPreferences) {
    private val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun write(name: String, clearText: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(clearText.toByteArray(Charsets.UTF_8))
        preferences.edit {
            putString("${name}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString("${name}_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    fun read(name: String): String? = runCatching {
        val iv = preferences.getString("${name}_iv", null) ?: return null
        val data = preferences.getString("${name}_data", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun delete(name: String) {
        preferences.edit {
            remove("${name}_iv")
            remove("${name}_data")
        }
    }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "a5_launcher_assistant_api_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
