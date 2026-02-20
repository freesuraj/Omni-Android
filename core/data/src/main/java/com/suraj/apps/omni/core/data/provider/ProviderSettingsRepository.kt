package com.suraj.apps.omni.core.data.provider

import android.content.Context
import androidx.annotation.StringRes
import com.suraj.apps.omni.core.data.R

class ProviderSettingsRepository(
    private val providerSettingsStore: ProviderSettingsStore,
    private val appContext: Context? = null
) {
    constructor(
        context: Context
    ) : this(
        providerSettingsStore = EncryptedPrefsProviderSettingsStore(context.applicationContext),
        appContext = context.applicationContext
    )

    fun loadSnapshot(): AiProviderSettingsSnapshot {
        val selectedProvider = providerSettingsStore.getSelectedProviderId()
        val options = AiProviderId.entries.map { provider ->
            AiProviderOption(
                providerId = provider,
                hasSavedApiKey = hasConfiguredApiKey(provider)
            )
        }
        return AiProviderSettingsSnapshot(
            selectedProvider = selectedProvider,
            options = options
        )
    }

    fun selectedProvider(): AiProviderId {
        return providerSettingsStore.getSelectedProviderId()
    }

    fun selectedAudioTranscriptionMode(): AudioTranscriptionMode {
        return providerSettingsStore.getAudioTranscriptionMode()
    }

    fun selectProvider(providerId: AiProviderId): AiProviderSettingsSnapshot {
        providerSettingsStore.setSelectedProviderId(providerId)
        return loadSnapshot()
    }

    fun selectAudioTranscriptionMode(mode: AudioTranscriptionMode) {
        providerSettingsStore.setAudioTranscriptionMode(mode)
    }

    fun hasConfiguredApiKey(providerId: AiProviderId): Boolean {
        return !providerSettingsStore.getApiKey(providerId).isNullOrBlank()
    }

    fun getApiKey(providerId: AiProviderId): String? {
        return providerSettingsStore.getApiKey(providerId)
    }

    fun saveApiKey(
        providerId: AiProviderId,
        rawApiKey: String
    ): ApiKeyValidationResult {
        if (!providerId.requiresApiKey) {
            return ApiKeyValidationResult(
                isValid = false,
                message = text(
                    R.string.provider_message_no_personal_key_required,
                    providerId.displayName
                )
            )
        }

        val normalized = rawApiKey.trim()
        if (!looksLikeApiKey(normalized)) {
            return ApiKeyValidationResult(
                isValid = false,
                message = text(R.string.provider_message_invalid_key)
            )
        }

        providerSettingsStore.setApiKey(providerId, normalized)
        return ApiKeyValidationResult(
            isValid = true,
            message = text(R.string.provider_message_key_saved, providerId.displayName)
        )
    }

    fun clearApiKey(providerId: AiProviderId) {
        providerSettingsStore.setApiKey(providerId, null)
    }

    fun validateProviderSelection(): ApiKeyValidationResult {
        val selectedProvider = selectedProvider()
        if (!selectedProvider.requiresApiKey) {
            return ApiKeyValidationResult(
                isValid = true,
                message = text(R.string.provider_message_ready, selectedProvider.displayName)
            )
        }

        val key = providerSettingsStore.getApiKey(selectedProvider)
        if (looksLikeApiKey(key.orEmpty())) {
            return ApiKeyValidationResult(
                isValid = true,
                message = text(
                    R.string.provider_message_configured,
                    selectedProvider.displayName
                )
            )
        }

        return ApiKeyValidationResult(
            isValid = false,
            message = text(
                R.string.provider_message_add_valid_key,
                selectedProvider.displayName
            )
        )
    }

    fun resolveProviderForGeneration(): ProviderResolution {
        val selectedProvider = selectedProvider()
        val apiKey = providerSettingsStore.getApiKey(selectedProvider)
            ?.takeIf { looksLikeApiKey(it) }
        return ProviderResolution(
            providerId = selectedProvider,
            apiKey = apiKey
        )
    }

    private fun looksLikeApiKey(candidate: String): Boolean {
        if (candidate.length < 20) return false
        return API_KEY_ALLOWED_PATTERN.matches(candidate)
    }

    companion object {
        private val API_KEY_ALLOWED_PATTERN = Regex("^[A-Za-z0-9._\\-]+$")
    }

    private fun text(
        @StringRes resId: Int,
        vararg args: Any
    ): String {
        val context = appContext
        if (context != null) {
            return context.getString(resId, *args)
        }
        return fallbackText(resId, args)
    }

    private fun fallbackText(
        @StringRes resId: Int,
        args: Array<out Any>
    ): String {
        val firstArg = args.firstOrNull()?.toString().orEmpty()
        return when (resId) {
            R.string.provider_message_no_personal_key_required ->
                "$firstArg does not require a personal API key."
            R.string.provider_message_invalid_key ->
                "API key looks invalid. Paste the full key and try again."
            R.string.provider_message_key_saved ->
                "API key saved for $firstArg."
            R.string.provider_message_ready ->
                "$firstArg is ready."
            R.string.provider_message_configured ->
                "$firstArg is configured."
            R.string.provider_message_add_valid_key ->
                "Add a valid API key for $firstArg in Settings."
            else -> ""
        }
    }
}
