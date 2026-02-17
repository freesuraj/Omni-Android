package com.suraj.apps.omni.core.data.provider

import android.content.Context
import com.suraj.apps.omni.core.model.QuizDifficulty
import java.util.Base64
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProviderQuizQuestion(
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val correctAnswer: String,
    val sourceSnippet: String?
)

data class ProviderStudyNote(
    val front: String,
    val back: String
)

data class ProviderExecution<T>(
    val value: T,
    val providerId: AiProviderId,
    val usedLocalFallback: Boolean
)

sealed interface ProviderGatewayResult<out T> {
    data class Success<T>(val execution: ProviderExecution<T>) : ProviderGatewayResult<T>
    data class Failure(
        val message: String,
        val providerId: AiProviderId
    ) : ProviderGatewayResult<Nothing>
}

interface StudyGenerationProvider {
    suspend fun generateQuizQuestions(
        sourceText: String,
        questionCount: Int,
        difficulty: QuizDifficulty,
        includeSnippet: Boolean
    ): List<ProviderQuizQuestion>

    suspend fun generateSummary(
        sourceText: String,
        targetWordCount: Int
    ): String

    suspend fun generateNotes(
        sourceText: String,
        targetCount: Int
    ): List<ProviderStudyNote>

    suspend fun answerQuestion(
        question: String,
        sourceText: String
    ): String

    suspend fun generateAnalysis(
        sourceText: String
    ): String
}

class StudyGenerationGateway(
    private val providerSettingsRepository: ProviderSettingsRepository,
    private val localProvider: StudyGenerationProvider = LocalHeuristicStudyGenerationProvider(),
    private val appContext: Context? = null,
    private val remoteProviderFactory: (AiProviderId, String) -> StudyGenerationProvider = ::defaultRemoteProvider,
    private val omniApiKeyProvider: () -> String? = ::defaultOmniApiKey
) {
    constructor(context: Context) : this(
        providerSettingsRepository = ProviderSettingsRepository(context),
        localProvider = LocalHeuristicStudyGenerationProvider(),
        appContext = context.applicationContext
    )

    suspend fun generateQuizQuestions(
        sourceText: String,
        questionCount: Int,
        difficulty: QuizDifficulty,
        includeSnippet: Boolean
    ): ProviderGatewayResult<List<ProviderQuizQuestion>> {
        return executeWithSelectedProvider { provider ->
            provider.generateQuizQuestions(
                sourceText = sourceText,
                questionCount = questionCount,
                difficulty = difficulty,
                includeSnippet = includeSnippet
            )
        }
    }

    suspend fun generateSummary(
        sourceText: String,
        targetWordCount: Int
    ): ProviderGatewayResult<String> {
        return executeWithSelectedProvider { provider ->
            provider.generateSummary(sourceText = sourceText, targetWordCount = targetWordCount)
        }
    }

    suspend fun generateNotes(
        sourceText: String,
        targetCount: Int
    ): ProviderGatewayResult<List<ProviderStudyNote>> {
        return executeWithSelectedProvider { provider ->
            provider.generateNotes(sourceText = sourceText, targetCount = targetCount)
        }
    }

    suspend fun answerQuestion(
        question: String,
        sourceText: String
    ): ProviderGatewayResult<String> {
        return executeWithSelectedProvider { provider ->
            provider.answerQuestion(question = question, sourceText = sourceText)
        }
    }

    suspend fun generateAnalysis(sourceText: String): ProviderGatewayResult<String> {
        return executeWithSelectedProvider { provider ->
            provider.generateAnalysis(sourceText = sourceText)
        }
    }

    private suspend fun <T> executeWithSelectedProvider(
        operation: suspend (StudyGenerationProvider) -> T
    ): ProviderGatewayResult<T> = withContext(Dispatchers.Default) {
        val resolution = providerSettingsRepository.resolveProviderForGeneration()
        val providerId = resolution.providerId

        val selection = when (val selected = selectProvider(providerId, resolution.apiKey)) {
            is ProviderSelection.Failure -> return@withContext selected.value
            is ProviderSelection.Ready -> selected
        }

        runCatching {
            operation(selection.provider)
        }.fold(
            onSuccess = { value ->
                ProviderGatewayResult.Success(
                    execution = ProviderExecution(
                        value = value,
                        providerId = providerId,
                        usedLocalFallback = selection.usedLocalFallback
                    )
                )
            },
            onFailure = { throwable ->
                ProviderGatewayResult.Failure(
                    message = throwable.message ?: (
                        appContext?.getString(
                            com.suraj.apps.omni.core.data.R.string.provider_generation_failed,
                            providerId.displayName
                        ) ?: "Generation failed for ${providerId.displayName}."
                        ),
                    providerId = providerId
                )
            }
        )
    }

    private fun selectProvider(
        providerId: AiProviderId,
        apiKey: String?
    ): ProviderSelection {
        if (providerId == AiProviderId.OMNI) {
            val omniApiKey = omniApiKeyProvider.invoke().orEmpty().trim()
            if (omniApiKey.isBlank()) {
                return ProviderSelection.Ready(
                    provider = localProvider,
                    usedLocalFallback = false
                )
            }

            return runCatching {
                ProviderSelection.Ready(
                    provider = remoteProviderFactory.invoke(AiProviderId.OMNI, omniApiKey),
                    usedLocalFallback = false
                )
            }.getOrElse { throwable ->
                ProviderSelection.Failure(generationFailure(providerId, throwable))
            }
        }

        if (providerId.requiresApiKey && apiKey.isNullOrBlank()) {
            return ProviderSelection.Failure(missingApiKeyFailure(providerId))
        }

        return runCatching {
            ProviderSelection.Ready(
                provider = remoteProviderFactory.invoke(providerId, apiKey.orEmpty()),
                usedLocalFallback = false
            )
        }.getOrElse { throwable ->
            ProviderSelection.Failure(generationFailure(providerId, throwable))
        }
    }

    private fun missingApiKeyFailure(providerId: AiProviderId): ProviderGatewayResult.Failure {
        return ProviderGatewayResult.Failure(
            message = appContext?.getString(
                com.suraj.apps.omni.core.data.R.string.provider_generation_missing_api_key,
                providerId.displayName
            ) ?: "${providerId.displayName} needs a valid API key. Open Settings to configure it.",
            providerId = providerId
        )
    }

    private fun generationFailure(
        providerId: AiProviderId,
        throwable: Throwable
    ): ProviderGatewayResult.Failure {
        return ProviderGatewayResult.Failure(
            message = throwable.message ?: (
                appContext?.getString(
                    com.suraj.apps.omni.core.data.R.string.provider_generation_failed,
                    providerId.displayName
                ) ?: "Generation failed for ${providerId.displayName}."
                ),
            providerId = providerId
        )
    }
}

private sealed interface ProviderSelection {
    data class Ready(
        val provider: StudyGenerationProvider,
        val usedLocalFallback: Boolean
    ) : ProviderSelection

    data class Failure(
        val value: ProviderGatewayResult.Failure
    ) : ProviderSelection
}

private const val OMNI_GEMINI_API_KEY_ENV = "OMNI_GEMINI_API_KEY"
private const val OMNI_GEMINI_API_KEY_PROPERTY = "omni.gemini.api.key"
private const val OMNI_GEMINI_API_KEY_B64 = "QUl6YVN5Qjdya0E1dXFCMTQ0MXpvbll3RmVaYm9MaVdDajJHNXhz"

private fun defaultOmniApiKey(): String? {
    val envValue = System.getenv(OMNI_GEMINI_API_KEY_ENV)?.trim().orEmpty()
    if (envValue.isNotBlank()) return envValue

    val propertyValue = System.getProperty(OMNI_GEMINI_API_KEY_PROPERTY)?.trim().orEmpty()
    if (propertyValue.isNotBlank()) return propertyValue

    val decodedFallback = runCatching {
        String(Base64.getDecoder().decode(OMNI_GEMINI_API_KEY_B64), Charsets.UTF_8).trim()
    }.getOrDefault("")
    return decodedFallback.ifBlank { null }
}

class LocalHeuristicStudyGenerationProvider : StudyGenerationProvider {
    override suspend fun generateQuizQuestions(
        sourceText: String,
        questionCount: Int,
        difficulty: QuizDifficulty,
        includeSnippet: Boolean
    ): List<ProviderQuizQuestion> = withContext(Dispatchers.Default) {
        val normalized = sourceText
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return@withContext emptyList()

        val sentences = normalized
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 32 }
            .ifEmpty {
                normalized
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.length >= 20 }
            }
            .take(120)
        if (sentences.isEmpty()) return@withContext emptyList()

        val keywordPool = sentences
            .flatMap { extractCandidateWords(it, QuizDifficulty.EASY) }
            .distinct()
            .ifEmpty { listOf("concept", "principle", "strategy") }

        val random = Random(System.currentTimeMillis())
        List(questionCount) { index ->
            val sentence = sentences[index % sentences.size]
            val keyword = pickKeyword(sentence, difficulty)
                ?: keywordPool[(index + random.nextInt(keywordPool.size)) % keywordPool.size]
            val distractor = keywordPool
                .firstOrNull { !it.equals(keyword, ignoreCase = true) && it.length >= keyword.length / 2 }
                ?: "uncertain outcome"

            val clozeSentence = sentence.replaceFirst(
                Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE),
                "_____"
            )
            val prompt = if (clozeSentence == sentence) {
                "Which statement best matches this source detail?\n$sentence"
            } else {
                "Which option best completes this statement?\n$clozeSentence"
            }

            val answerAsA = random.nextBoolean()
            val optionA = if (answerAsA) keyword else distractor
            val optionB = if (answerAsA) distractor else keyword

            ProviderQuizQuestion(
                prompt = prompt,
                optionA = optionA.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                optionB = optionB.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                correctAnswer = if (answerAsA) "A" else "B",
                sourceSnippet = if (includeSnippet) sentence else null
            )
        }
    }

    override suspend fun generateSummary(
        sourceText: String,
        targetWordCount: Int
    ): String = withContext(Dispatchers.Default) {
        val sentences = sourceText
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 24 }
        if (sentences.isEmpty()) return@withContext ""

        val selected = mutableListOf<String>()
        var currentWords = 0
        for (sentence in sentences) {
            selected += sentence
            currentWords += sentence.wordCount()
            if (currentWords >= targetWordCount) break
        }
        if (selected.isEmpty()) return@withContext ""

        val merged = selected.joinToString(" ")
        val words = merged.split(Regex("\\s+"))
        if (words.size <= targetWordCount) return@withContext merged

        words.take(targetWordCount).joinToString(" ").trimEnd('.', '!', '?') + "."
    }

    override suspend fun generateNotes(
        sourceText: String,
        targetCount: Int
    ): List<ProviderStudyNote> = withContext(Dispatchers.Default) {
        val sentences = sourceText
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 18 }
        if (sentences.isEmpty()) return@withContext emptyList()

        val chunks = sentences.chunked(2)
        List(targetCount) { index ->
            val chunk = chunks[index % chunks.size]
            val leadSentence = chunk.first()
            ProviderStudyNote(
                front = buildFrontPrompt(leadSentence),
                back = chunk.joinToString(" ")
            )
        }
    }

    override suspend fun answerQuestion(
        question: String,
        sourceText: String
    ): String = withContext(Dispatchers.Default) {
        val sentences = sourceText
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 24 }
        if (sentences.isEmpty()) {
            return@withContext "I couldn't find enough readable content in this document to answer that yet."
        }

        val questionTerms = tokenize(question)
        val ranked = if (questionTerms.isEmpty()) {
            sentences.take(3)
        } else {
            sentences
                .map { sentence ->
                    val sentenceTerms = tokenize(sentence)
                    val overlap = questionTerms.count { term -> term in sentenceTerms }
                    sentence to overlap
                }
                .sortedWith(
                    compareByDescending<Pair<String, Int>> { it.second }
                        .thenByDescending { it.first.length }
                )
                .take(3)
                .map { it.first }
        }

        val selected = if (ranked.all { sentence -> sentence.isBlank() }) {
            sentences.take(2)
        } else {
            ranked.filter { it.isNotBlank() }
        }.ifEmpty { sentences.take(2) }

        selected.joinToString(" ").limitWordCount(MAX_ANSWER_WORDS)
    }

    override suspend fun generateAnalysis(sourceText: String): String = withContext(Dispatchers.Default) {
        val condensed = sourceText
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .take(120)
            .joinToString(" ")

        if (condensed.isBlank()) {
            "No analyzable content found yet."
        } else {
            "Key themes: $condensed"
        }
    }
}

private fun pickKeyword(
    sentence: String,
    difficulty: QuizDifficulty
): String? {
    val candidates = extractCandidateWords(sentence, difficulty)
    if (candidates.isEmpty()) return null
    return candidates.maxByOrNull { it.length }
}

private fun extractCandidateWords(
    sentence: String,
    difficulty: QuizDifficulty
): List<String> {
    val minLength = when (difficulty) {
        QuizDifficulty.EASY -> 4
        QuizDifficulty.MEDIUM -> 6
        QuizDifficulty.HARD -> 8
    }

    return sentence
        .split(Regex("[^A-Za-z0-9]+"))
        .map { it.trim() }
        .filter { token ->
            token.length >= minLength &&
                token.none { it.isDigit() } &&
                token.lowercase() !in COMMON_STOP_WORDS
        }
}

private fun buildFrontPrompt(sentence: String): String {
    if (sentence.endsWith("?")) return sentence

    val tokens = sentence
        .split(Regex("[^A-Za-z0-9]+"))
        .map { it.trim() }
        .filter { it.length >= 5 }

    val keyword = tokens.maxByOrNull { it.length }
    return if (keyword == null) {
        "What key idea is described in this note?"
    } else {
        "How does $keyword relate to this document?"
    }
}

private fun tokenize(text: String): Set<String> {
    return text
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .asSequence()
        .map { it.trim() }
        .filter { it.length >= 3 && it !in QA_STOP_WORDS }
        .toSet()
}

private fun String.limitWordCount(maxWords: Int): String {
    val words = trim().split(Regex("\\s+"))
    if (words.size <= maxWords) return this
    return words.take(maxWords).joinToString(" ").trimEnd('.', '!', '?') + "."
}

private fun String.wordCount(): Int {
    return trim().split(Regex("\\s+")).count { it.isNotBlank() }
}

private const val MAX_ANSWER_WORDS = 90

private val COMMON_STOP_WORDS = setOf(
    "about",
    "after",
    "also",
    "because",
    "between",
    "could",
    "different",
    "during",
    "first",
    "from",
    "have",
    "into",
    "other",
    "their",
    "there",
    "these",
    "those",
    "through",
    "under",
    "using",
    "which",
    "while",
    "with",
    "without"
)

private val QA_STOP_WORDS = setOf(
    "the", "and", "for", "with", "that", "this", "from", "into", "what", "which", "when",
    "where", "does", "have", "about", "using", "there", "their", "they", "them", "your",
    "you", "are", "was", "were", "been", "being", "will", "would", "could", "should", "not",
    "but", "all", "any", "can", "how", "why", "who", "whose", "our", "out", "has", "had"
)
