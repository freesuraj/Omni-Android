package com.suraj.apps.omni.core.data.provider

import android.content.Context

class ProviderSettingsRepository(
    private val providerSettingsStore: ProviderSettingsStore
) {
    constructor(
        context: Context
    ) : this(
        providerSettingsStore = EncryptedPrefsProviderSettingsStore(context.applicationContext)
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

    fun selectProvider(providerId: AiProviderId): AiProviderSettingsSnapshot {
        providerSettingsStore.setSelectedProviderId(providerId)
        return loadSnapshot()
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
                message = "${providerId.displayName} does not require a personal API key."
            )
        }

        val normalized = rawApiKey.trim()
        if (!looksLikeApiKey(normalized)) {
            return ApiKeyValidationResult(
                isValid = false,
                message = "API key looks invalid. Paste the full key and try again."
            )
        }

        providerSettingsStore.setApiKey(providerId, normalized)
        return ApiKeyValidationResult(
            isValid = true,
            message = "API key saved for ${providerId.displayName}."
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
                message = "${selectedProvider.displayName} is ready."
            )
        }

        val key = providerSettingsStore.getApiKey(selectedProvider)
        if (looksLikeApiKey(key.orEmpty())) {
            return ApiKeyValidationResult(
                isValid = true,
                message = "${selectedProvider.displayName} is configured."
            )
        }

        return ApiKeyValidationResult(
            isValid = false,
            message = "Add a valid API key for ${selectedProvider.displayName} in Settings."
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
}
