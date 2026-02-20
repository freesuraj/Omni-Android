package com.suraj.apps.omni.core.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSettingsRepositoryTest {
    private val store = InMemoryProviderSettingsStore()
    private val repository = ProviderSettingsRepository(store)

    @Test
    fun defaultSelectionIsOmniWithoutApiKeyRequirement() {
        val snapshot = repository.loadSnapshot()

        assertEquals(AiProviderId.OMNI, snapshot.selectedProvider)
        assertTrue(repository.validateProviderSelection().isValid)
    }

    @Test
    fun selectingExternalProviderRequiresValidApiKeyBeforeReady() {
        repository.selectProvider(AiProviderId.GEMINI)

        val missingKeyValidation = repository.validateProviderSelection()
        assertFalse(missingKeyValidation.isValid)

        val invalidSave = repository.saveApiKey(AiProviderId.GEMINI, "short-key")
        assertFalse(invalidSave.isValid)

        val validSave = repository.saveApiKey(AiProviderId.GEMINI, "AIzaSyA_VALID_EXAMPLE_1234567890")
        assertTrue(validSave.isValid)
        assertTrue(repository.validateProviderSelection().isValid)
        assertTrue(repository.hasConfiguredApiKey(AiProviderId.GEMINI))
    }

    @Test
    fun clearApiKeyRemovesStoredCredential() {
        repository.selectProvider(AiProviderId.OPENAI)
        repository.saveApiKey(AiProviderId.OPENAI, "sk-example-token-ABCDEFGHIJKLMNOPQRSTUVWXYZ")

        repository.clearApiKey(AiProviderId.OPENAI)

        assertFalse(repository.hasConfiguredApiKey(AiProviderId.OPENAI))
        assertFalse(repository.validateProviderSelection().isValid)
    }
}

private class InMemoryProviderSettingsStore : ProviderSettingsStore {
    private var selectedProvider = AiProviderId.OMNI
    private var transcriptionMode = AudioTranscriptionMode.ON_DEVICE
    private val apiKeys = mutableMapOf<AiProviderId, String>()

    override fun getSelectedProviderId(): AiProviderId = selectedProvider

    override fun setSelectedProviderId(providerId: AiProviderId) {
        selectedProvider = providerId
    }

    override fun getAudioTranscriptionMode(): AudioTranscriptionMode = transcriptionMode

    override fun setAudioTranscriptionMode(mode: AudioTranscriptionMode) {
        transcriptionMode = mode
    }

    override fun getApiKey(providerId: AiProviderId): String? {
        return apiKeys[providerId]
    }

    override fun setApiKey(providerId: AiProviderId, apiKey: String?) {
        if (apiKey.isNullOrBlank()) {
            apiKeys.remove(providerId)
        } else {
            apiKeys[providerId] = apiKey
        }
    }
}
