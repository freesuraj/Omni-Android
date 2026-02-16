package com.suraj.apps.omni.core.data.quiz

import android.content.Context
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.importing.SharedPrefsPremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.OmniDatabaseFactory
import com.suraj.apps.omni.core.data.local.entity.QuestionEntity
import com.suraj.apps.omni.core.data.local.entity.QuizEntity
import com.suraj.apps.omni.core.data.local.model.QuizWithQuestions
import com.suraj.apps.omni.core.model.QuestionStatus
import com.suraj.apps.omni.core.model.QuizDifficulty
import com.suraj.apps.omni.core.model.QuizSettings
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MIN_QUIZ_QUESTIONS = 5
const val FREE_MAX_QUIZ_QUESTIONS = 10
const val PREMIUM_MAX_QUIZ_QUESTIONS = 20

data class QuizSnapshot(
    val documentTitle: String,
    val quiz: QuizEntity,
    val questions: List<QuestionEntity>,
    val isPremiumUnlocked: Boolean
) {
    fun isCompleted(): Boolean =
        quiz.completedAtEpochMs != null || questions.all { !it.userAnswer.isNullOrBlank() }
}

data class QuizBootstrap(
    val documentTitle: String,
    val isPremiumUnlocked: Boolean,
    val latestQuiz: QuizSnapshot?
)

data class QuizGenerationSuccess(
    val snapshot: QuizSnapshot,
    val usedFallbackGenerator: Boolean
)

sealed interface QuizGenerationResult {
    data class Success(val data: QuizGenerationSuccess) : QuizGenerationResult
    data object RequiresPremium : QuizGenerationResult
    data class Failure(val message: String) : QuizGenerationResult
}

data class QuizAnswerResult(
    val snapshot: QuizSnapshot,
    val wasCorrect: Boolean
)

class QuizRepository(
    context: Context,
    private val database: OmniDatabase = OmniDatabaseFactory.create(context),
    private val premiumAccessChecker: PremiumAccessChecker = SharedPrefsPremiumAccessChecker(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val importRepository = DocumentImportRepository(
        context = context,
        database = database,
        premiumAccessChecker = premiumAccessChecker,
        ioDispatcher = ioDispatcher
    )

    suspend fun loadBootstrap(documentId: String): QuizBootstrap? = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId) ?: return@withContext null
        val premiumUnlocked = importRepository.isPremiumUnlocked()
        val latest = database.quizDao().getLatestQuizWithQuestions(documentId)
            ?.toSnapshot(documentTitle = document.title, isPremiumUnlocked = premiumUnlocked)

        QuizBootstrap(
            documentTitle = document.title,
            isPremiumUnlocked = premiumUnlocked,
            latestQuiz = latest
        )
    }

    suspend fun loadQuiz(quizId: String): QuizSnapshot? = withContext(ioDispatcher) {
        val aggregate = database.quizDao().getQuizWithQuestions(quizId) ?: return@withContext null
        aggregate.toSnapshotWithDocumentTitle()
    }

    suspend fun generateQuiz(
        documentId: String,
        requestedSettings: QuizSettings
    ): QuizGenerationResult = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext QuizGenerationResult.Failure("Document not found.")

        val isPremiumUnlocked = importRepository.isPremiumUnlocked()
        if (!isPremiumUnlocked && requestedSettings.questionCount > FREE_MAX_QUIZ_QUESTIONS) {
            return@withContext QuizGenerationResult.RequiresPremium
        }

        val maxQuestions = if (isPremiumUnlocked) PREMIUM_MAX_QUIZ_QUESTIONS else FREE_MAX_QUIZ_QUESTIONS
        val settings = requestedSettings.copy(
            questionCount = requestedSettings.questionCount.coerceIn(MIN_QUIZ_QUESTIONS, maxQuestions)
        )

        val fullText = importRepository.readFullText(documentId).orEmpty().trim()
        if (fullText.isBlank()) {
            return@withContext QuizGenerationResult.Failure(
                "This document is still processing. Try generating quiz questions in a moment."
            )
        }

        val drafts = buildQuestionDrafts(
            sourceText = fullText,
            questionCount = settings.questionCount,
            difficulty = settings.difficulty,
            includeSnippet = settings.showSourceSnippet
        )

        if (drafts.isEmpty()) {
            return@withContext QuizGenerationResult.Failure(
                "Could not generate enough quiz questions from this source."
            )
        }

        val now = System.currentTimeMillis()
        val quizId = UUID.randomUUID().toString()
        val quiz = QuizEntity(
            id = quizId,
            documentId = documentId,
            createdAtEpochMs = now,
            settings = settings,
            currentIndex = 0,
            correctCount = 0,
            completedAtEpochMs = null,
            isReview = false
        )
        val questions = drafts.mapIndexed { index, draft ->
            QuestionEntity(
                id = UUID.randomUUID().toString(),
                quizId = quizId,
                prompt = draft.prompt,
                optionA = draft.optionA,
                optionB = draft.optionB,
                correctAnswer = draft.correctAnswer,
                sourceSnippet = draft.sourceSnippet,
                userAnswer = null,
                isCorrect = null,
                createdFromChunkIndex = index,
                previousStatus = QuestionStatus.NEW
            )
        }

        database.quizDao().upsertQuiz(quiz)
        database.quizDao().upsertQuestions(questions)

        val persisted = database.quizDao().getQuizWithQuestions(quizId)
            ?: return@withContext QuizGenerationResult.Failure("Generated quiz could not be loaded.")

        QuizGenerationResult.Success(
            data = QuizGenerationSuccess(
                snapshot = persisted.toSnapshot(
                    documentTitle = document.title,
                    isPremiumUnlocked = isPremiumUnlocked
                ),
                usedFallbackGenerator = true
            )
        )
    }

    suspend fun replayQuiz(quizId: String): QuizGenerationResult = withContext(ioDispatcher) {
        val aggregate = database.quizDao().getQuizWithQuestions(quizId)
            ?: return@withContext QuizGenerationResult.Failure("Quiz not found.")

        val sourceQuiz = aggregate.quiz
        val documentId = sourceQuiz.documentId
            ?: return@withContext QuizGenerationResult.Failure("Quiz is not attached to a document.")
        val document = database.documentDao().getById(documentId)
            ?: return@withContext QuizGenerationResult.Failure("Document not found.")

        val newQuizId = UUID.randomUUID().toString()
        val newQuiz = sourceQuiz.copy(
            id = newQuizId,
            createdAtEpochMs = System.currentTimeMillis(),
            currentIndex = 0,
            correctCount = 0,
            completedAtEpochMs = null,
            isReview = false
        )
        val newQuestions = aggregate.questions.mapIndexed { index, question ->
            question.copy(
                id = UUID.randomUUID().toString(),
                quizId = newQuizId,
                userAnswer = null,
                isCorrect = null,
                createdFromChunkIndex = index,
                previousStatus = QuestionStatus.NEW
            )
        }

        database.quizDao().upsertQuiz(newQuiz)
        database.quizDao().upsertQuestions(newQuestions)

        val premiumUnlocked = importRepository.isPremiumUnlocked()
        val snapshot = database.quizDao().getQuizWithQuestions(newQuizId)
            ?.toSnapshot(document.title, premiumUnlocked)
            ?: return@withContext QuizGenerationResult.Failure("Unable to open replay quiz.")

        QuizGenerationResult.Success(
            data = QuizGenerationSuccess(
                snapshot = snapshot,
                usedFallbackGenerator = false
            )
        )
    }

    suspend fun answerQuestion(
        quizId: String,
        questionId: String,
        selectedAnswer: String
    ): QuizAnswerResult? = withContext(ioDispatcher) {
        val normalizedAnswer = selectedAnswer.uppercase(Locale.US)
        if (normalizedAnswer != "A" && normalizedAnswer != "B") {
            return@withContext null
        }

        val aggregate = database.quizDao().getQuizWithQuestions(quizId) ?: return@withContext null
        val question = aggregate.questions.firstOrNull { it.id == questionId } ?: return@withContext null
        if (!question.userAnswer.isNullOrBlank()) {
            val existingSnapshot = aggregate.toSnapshotWithDocumentTitle() ?: return@withContext null
            return@withContext QuizAnswerResult(
                snapshot = existingSnapshot,
                wasCorrect = question.isCorrect == true
            )
        }

        val wasCorrect = question.correctAnswer.equals(normalizedAnswer, ignoreCase = true)
        database.quizDao().upsertQuestion(
            question.copy(
                userAnswer = normalizedAnswer,
                isCorrect = wasCorrect,
                previousStatus = if (wasCorrect) QuestionStatus.CORRECT else QuestionStatus.INCORRECT
            )
        )

        val refreshed = database.quizDao().getQuizWithQuestions(quizId) ?: return@withContext null
        val answeredCount = refreshed.questions.count { !it.userAnswer.isNullOrBlank() }
        val correctCount = refreshed.questions.count { it.isCorrect == true }
        val completed = answeredCount == refreshed.questions.size
        val nextIndex = if (completed) {
            (refreshed.questions.lastIndex).coerceAtLeast(0)
        } else {
            refreshed.questions.indexOfFirst { it.userAnswer.isNullOrBlank() }.coerceAtLeast(0)
        }

        database.quizDao().upsertQuiz(
            refreshed.quiz.copy(
                currentIndex = nextIndex,
                correctCount = correctCount,
                completedAtEpochMs = if (completed) System.currentTimeMillis() else null,
                isReview = completed
            )
        )

        val latest = database.quizDao().getQuizWithQuestions(quizId)?.toSnapshotWithDocumentTitle()
            ?: return@withContext null
        QuizAnswerResult(snapshot = latest, wasCorrect = wasCorrect)
    }

    private suspend fun QuizWithQuestions.toSnapshotWithDocumentTitle(): QuizSnapshot? {
        val documentId = quiz.documentId ?: return null
        val document = database.documentDao().getById(documentId) ?: return null
        return toSnapshot(
            documentTitle = document.title,
            isPremiumUnlocked = importRepository.isPremiumUnlocked()
        )
    }

    private fun QuizWithQuestions.toSnapshot(
        documentTitle: String,
        isPremiumUnlocked: Boolean
    ): QuizSnapshot {
        return QuizSnapshot(
            documentTitle = documentTitle,
            quiz = quiz,
            questions = questions.sortedBy { it.createdFromChunkIndex ?: Int.MAX_VALUE },
            isPremiumUnlocked = isPremiumUnlocked
        )
    }
}

private data class GeneratedQuestionDraft(
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val correctAnswer: String,
    val sourceSnippet: String?
)

private fun buildQuestionDrafts(
    sourceText: String,
    questionCount: Int,
    difficulty: QuizDifficulty,
    includeSnippet: Boolean
): List<GeneratedQuestionDraft> {
    val normalized = sourceText
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return emptyList()

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

    if (sentences.isEmpty()) return emptyList()

    val keywordPool = sentences
        .flatMap { extractCandidateWords(it, QuizDifficulty.EASY) }
        .distinct()
        .ifEmpty { listOf("concept", "principle", "strategy") }

    val random = Random(System.currentTimeMillis())
    return List(questionCount) { index ->
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

        GeneratedQuestionDraft(
            prompt = prompt,
            optionA = optionA.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            optionB = optionB.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            correctAnswer = if (answerAsA) "A" else "B",
            sourceSnippet = if (includeSnippet) sentence else null
        )
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
                token.lowercase(Locale.US) !in COMMON_STOP_WORDS
        }
}

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
