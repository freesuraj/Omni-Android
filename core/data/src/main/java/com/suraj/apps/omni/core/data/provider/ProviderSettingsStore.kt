package com.suraj.apps.omni.core.data.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

const val PROVIDER_PREFS_NAME = "omni_provider_settings"
private const val KEY_SELECTED_PROVIDER = "selected_provider"
private const val KEY_AUDIO_TRANSCRIPTION_MODE = "audio_transcription_mode"

interface ProviderSettingsStore {
    fun getSelectedProviderId(): AiProviderId
    fun setSelectedProviderId(providerId: AiProviderId)
    fun getAudioTranscriptionMode(): AudioTranscriptionMode
    fun setAudioTranscriptionMode(mode: AudioTranscriptionMode)
    fun getApiKey(providerId: AiProviderId): String?
    fun setApiKey(providerId: AiProviderId, apiKey: String?)
}

class EncryptedPrefsProviderSettingsStore(
    context: Context
) : ProviderSettingsStore {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PROVIDER_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        // Keep app functional when secure prefs initialization is unavailable in local environments.
        appContext.getSharedPreferences(PROVIDER_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getSelectedProviderId(): AiProviderId {
        val rawValue = prefs.getString(KEY_SELECTED_PROVIDER, null)
        return AiProviderId.fromStorageKey(rawValue)
    }

    override fun setSelectedProviderId(providerId: AiProviderId) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, providerId.storageKey).apply()
    }

    override fun getAudioTranscriptionMode(): AudioTranscriptionMode {
        val rawValue = prefs.getString(KEY_AUDIO_TRANSCRIPTION_MODE, null)
        return AudioTranscriptionMode.fromStorageKey(rawValue)
    }

    override fun setAudioTranscriptionMode(mode: AudioTranscriptionMode) {
        prefs.edit().putString(KEY_AUDIO_TRANSCRIPTION_MODE, mode.storageKey).apply()
    }

    override fun getApiKey(providerId: AiProviderId): String? {
        return prefs.getString(apiKeyStorageName(providerId), null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    override fun setApiKey(providerId: AiProviderId, apiKey: String?) {
        val normalized = apiKey?.trim().orEmpty()
        val editor = prefs.edit()
        if (normalized.isBlank()) {
            editor.remove(apiKeyStorageName(providerId))
        } else {
            editor.putString(apiKeyStorageName(providerId), normalized)
        }
        editor.apply()
    }

    private fun apiKeyStorageName(providerId: AiProviderId): String {
        return "provider_api_key_${providerId.storageKey}"
    }
}
