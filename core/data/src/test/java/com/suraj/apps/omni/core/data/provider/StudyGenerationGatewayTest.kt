package com.suraj.apps.omni.core.data.provider

import com.suraj.apps.omni.core.model.QuizDifficulty
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGenerationGatewayTest {
    @Test
    fun gatewayRejectsExternalProviderWithoutKey() = runBlocking {
        val repository = ProviderSettingsRepository(GatewayTestStore().apply {
            setSelectedProviderId(AiProviderId.OPENAI)
        })
        val fakeProvider = FakeStudyGenerationProvider()
        val gateway = StudyGenerationGateway(
            providerSettingsRepository = repository,
            localProvider = fakeProvider,
            remoteProviderFactory = { _, _ -> fakeProvider }
        )

        val result = gateway.generateSummary("source text", 100)

        assertTrue(result is ProviderGatewayResult.Failure)
    }

    @Test
    fun gatewayRunsWithConfiguredExternalProviderWhenKeyExists() = runBlocking {
        val store = GatewayTestStore().apply {
            setSelectedProviderId(AiProviderId.GEMINI)
            setApiKey(AiProviderId.GEMINI, "AIzaSyA_VALID_EXAMPLE_1234567890")
        }
        val repository = ProviderSettingsRepository(store)
        val fakeProvider = FakeStudyGenerationProvider()
        val gateway = StudyGenerationGateway(
            providerSettingsRepository = repository,
            localProvider = fakeProvider,
            remoteProviderFactory = { _, _ -> fakeProvider }
        )

        val result = gateway.generateSummary("source text", 100)

        val success = result as ProviderGatewayResult.Success
        assertEquals("summary-output", success.execution.value)
        assertEquals(AiProviderId.GEMINI, success.execution.providerId)
        assertFalse(success.execution.usedLocalFallback)
    }

    @Test
    fun gatewayUsesPrimaryPathForOmniProvider() = runBlocking {
        val fakeProvider = FakeStudyGenerationProvider()
        val repository = ProviderSettingsRepository(GatewayTestStore())
        val gateway = StudyGenerationGateway(
            providerSettingsRepository = repository,
            localProvider = fakeProvider,
            remoteProviderFactory = { _, _ -> fakeProvider }
        )

        val result = gateway.generateQuizQuestions(
            sourceText = "Bitcoin secures blocks with proof of work.",
            questionCount = 1,
            difficulty = QuizDifficulty.EASY,
            includeSnippet = false
        )

        val success = result as ProviderGatewayResult.Success
        assertFalse(success.execution.usedLocalFallback)
        assertEquals(AiProviderId.OMNI, success.execution.providerId)
        assertEquals(1, success.execution.value.size)
    }

    @Test
    fun gatewayUsesGeminiBackedOmniProviderWhenInternalKeyExists() = runBlocking {
        val localProvider = FakeStudyGenerationProvider(summaryOutput = "local-summary")
        val remoteProvider = FakeStudyGenerationProvider(summaryOutput = "remote-summary")
        var capturedProviderId: AiProviderId? = null
        var capturedApiKey: String? = null

        val repository = ProviderSettingsRepository(GatewayTestStore())
        val gateway = StudyGenerationGateway(
            providerSettingsRepository = repository,
            localProvider = localProvider,
            remoteProviderFactory = { providerId, apiKey ->
                capturedProviderId = providerId
                capturedApiKey = apiKey
                remoteProvider
            },
            omniApiKeyProvider = { "omni-internal-key" }
        )

        val result = gateway.generateSummary("source text", 100)

        val success = result as ProviderGatewayResult.Success
        assertEquals("remote-summary", success.execution.value)
        assertEquals(AiProviderId.OMNI, capturedProviderId)
        assertEquals("omni-internal-key", capturedApiKey)
        assertFalse(success.execution.usedLocalFallback)
    }
}

private class FakeStudyGenerationProvider(
    private val summaryOutput: String = "summary-output"
) : StudyGenerationProvider {
    override suspend fun generateQuizQuestions(
        sourceText: String,
        questionCount: Int,
        difficulty: QuizDifficulty,
        includeSnippet: Boolean
    ): List<ProviderQuizQuestion> {
        return List(questionCount) {
            ProviderQuizQuestion(
                prompt = "prompt",
                optionA = "A",
                optionB = "B",
                correctAnswer = "A",
                sourceSnippet = null
            )
        }
    }

    override suspend fun generateSummary(sourceText: String, targetWordCount: Int): String {
        return summaryOutput
    }

    override suspend fun generateNotes(sourceText: String, targetCount: Int): List<ProviderStudyNote> {
        return List(targetCount) { ProviderStudyNote(front = "front", back = "back") }
    }

    override suspend fun answerQuestion(question: String, sourceText: String): String {
        return "answer-output"
    }

    override suspend fun generateAnalysis(sourceText: String): String {
        return "analysis-output"
    }
}

private class GatewayTestStore : ProviderSettingsStore {
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
