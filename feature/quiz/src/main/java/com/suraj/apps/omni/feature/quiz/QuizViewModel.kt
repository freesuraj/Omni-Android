package com.suraj.apps.omni.feature.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.quiz.FREE_MAX_QUIZ_QUESTIONS
import com.suraj.apps.omni.core.data.quiz.MIN_QUIZ_QUESTIONS
import com.suraj.apps.omni.core.data.quiz.PREMIUM_MAX_QUIZ_QUESTIONS
import com.suraj.apps.omni.core.data.quiz.QuizGenerationResult
import com.suraj.apps.omni.core.data.quiz.QuizRepository
import com.suraj.apps.omni.core.data.quiz.QuizSnapshot
import com.suraj.apps.omni.core.model.QuizDifficulty
import com.suraj.apps.omni.core.model.QuizSettings
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizScreenMode {
    CONFIG,
    GENERATING,
    PLAYING,
    RESULT
}

data class QuizQuestionUi(
    val id: String,
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val correctAnswer: String,
    val sourceSnippet: String?,
    val userAnswer: String?,
    val isCorrect: Boolean?
)

data class QuizHistoryPreview(
    val quizId: String,
    val completed: Boolean,
    val answeredCount: Int,
    val questionCount: Int,
    val correctCount: Int
)

data class AnswerFeedback(
    val wasCorrect: Boolean,
    val nonce: Long
)

data class QuizUiState(
    val documentTitle: String = "",
    val mode: QuizScreenMode = QuizScreenMode.CONFIG,
    val settings: QuizSettings = QuizSettings(questionCount = FREE_MAX_QUIZ_QUESTIONS),
    val isPremiumUnlocked: Boolean = false,
    val maxQuestionCount: Int = FREE_MAX_QUIZ_QUESTIONS,
    val activeQuizId: String? = null,
    val questions: List<QuizQuestionUi> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val correctCount: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val historyPreview: QuizHistoryPreview? = null,
    val providerNotice: String? = null,
    val answerFeedback: AnswerFeedback? = null,
    val shouldOpenPaywall: Boolean = false,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
) {
    val questionCountLabel: String get() = "${settings.questionCount}"
}

class QuizViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val app = application
    private val repository = QuizRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        QuizUiState(documentTitle = app.getString(R.string.quiz_title_configure))
    )
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadBootstrap()
        }
    }

    fun onQuestionCountChanged(questionCount: Int) {
        _uiState.update { state ->
            val clamped = questionCount.coerceIn(MIN_QUIZ_QUESTIONS, state.maxQuestionCount)
            state.copy(settings = state.settings.copy(questionCount = clamped))
        }
    }

    fun onDifficultyChanged(difficulty: QuizDifficulty) {
        _uiState.update { it.copy(settings = it.settings.copy(difficulty = difficulty)) }
    }

    fun onShowSourceSnippetChanged(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(showSourceSnippet = enabled)) }
    }

    fun onSoundsEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(soundsEnabled = enabled)) }
    }

    fun generateQuiz() {
        val currentState = _uiState.value
        if (!currentState.isPremiumUnlocked && currentState.settings.questionCount > FREE_MAX_QUIZ_QUESTIONS) {
            _uiState.update {
                it.copy(
                    shouldOpenPaywall = true,
                    errorMessage = app.getString(
                        R.string.quiz_error_free_limit,
                        FREE_MAX_QUIZ_QUESTIONS
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = QuizScreenMode.GENERATING,
                    isBusy = true,
                    errorMessage = null,
                    providerNotice = null,
                    answerFeedback = null,
                    streak = 0,
                    bestStreak = 0
                )
            }

            delay(500L)
            when (val result = repository.generateQuiz(documentId, _uiState.value.settings)) {
                is QuizGenerationResult.Success -> {
                    applySnapshot(
                        snapshot = result.data.snapshot,
                        providerNotice = if (result.data.usedFallbackGenerator) {
                            app.getString(R.string.quiz_provider_notice_fallback)
                        } else {
                            null
                        },
                        resetStreak = true
                    )
                }

                QuizGenerationResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            mode = QuizScreenMode.CONFIG,
                            isBusy = false,
                            shouldOpenPaywall = true,
                            errorMessage = app.getString(R.string.quiz_error_requires_premium_configuration)
                        )
                    }
                }

                is QuizGenerationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            mode = QuizScreenMode.CONFIG,
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun answerCurrentQuestion(answer: String) {
        val state = _uiState.value
        val quizId = state.activeQuizId ?: return
        if (state.mode != QuizScreenMode.PLAYING) return

        val currentQuestion = state.questions
            .firstOrNull { it.userAnswer == null }
            ?: state.questions.getOrNull(state.currentQuestionIndex)
            ?: return

        viewModelScope.launch {
            val result = repository.answerQuestion(
                quizId = quizId,
                questionId = currentQuestion.id,
                selectedAnswer = answer
            ) ?: return@launch

            val nextStreak = if (result.wasCorrect) state.streak + 1 else 0
            val nextBest = max(state.bestStreak, nextStreak)
            applySnapshot(
                snapshot = result.snapshot,
                providerNotice = state.providerNotice,
                resetStreak = false,
                streak = nextStreak,
                bestStreak = nextBest,
                feedback = AnswerFeedback(
                    wasCorrect = result.wasCorrect,
                    nonce = System.currentTimeMillis()
                )
            )
        }
    }

    fun openHistoryReview() {
        val preview = _uiState.value.historyPreview ?: return
        viewModelScope.launch {
            val snapshot = repository.loadQuiz(preview.quizId)
            if (snapshot == null) {
                _uiState.update { it.copy(errorMessage = app.getString(R.string.quiz_error_load_history_failed)) }
                return@launch
            }
            applySnapshot(
                snapshot = snapshot,
                providerNotice = _uiState.value.providerNotice,
                resetStreak = true
            )
        }
    }

    fun replayHistoryQuiz() {
        val preview = _uiState.value.historyPreview ?: return
        replayQuiz(preview.quizId)
    }

    fun replayActiveQuiz() {
        val quizId = _uiState.value.activeQuizId ?: return
        replayQuiz(quizId)
    }

    fun backToConfiguration() {
        _uiState.update {
            it.copy(
                mode = QuizScreenMode.CONFIG,
                answerFeedback = null,
                streak = 0,
                bestStreak = 0
            )
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumePaywallNavigation() {
        _uiState.update { it.copy(shouldOpenPaywall = false) }
    }

    fun consumeAnswerFeedback() {
        _uiState.update { it.copy(answerFeedback = null) }
    }

    private suspend fun loadBootstrap() {
        val bootstrap = repository.loadBootstrap(documentId)
        if (bootstrap == null) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.quiz_error_document_not_found)) }
            return
        }

        val maxQuestions = if (bootstrap.isPremiumUnlocked) {
            PREMIUM_MAX_QUIZ_QUESTIONS
        } else {
            FREE_MAX_QUIZ_QUESTIONS
        }

        val seedSettings = (bootstrap.latestQuiz?.quiz?.settings ?: QuizSettings(questionCount = 10)).copy(
            questionCount = (bootstrap.latestQuiz?.quiz?.settings?.questionCount ?: 10)
                .coerceIn(MIN_QUIZ_QUESTIONS, maxQuestions)
        )

        _uiState.update {
            it.copy(
                documentTitle = bootstrap.documentTitle,
                settings = seedSettings,
                isPremiumUnlocked = bootstrap.isPremiumUnlocked,
                maxQuestionCount = maxQuestions,
                historyPreview = bootstrap.latestQuiz?.toHistoryPreview(),
                errorMessage = null,
                mode = QuizScreenMode.CONFIG,
                isBusy = false
            )
        }
    }

    private fun replayQuiz(quizId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = QuizScreenMode.GENERATING,
                    isBusy = true,
                    errorMessage = null,
                    answerFeedback = null,
                    streak = 0,
                    bestStreak = 0
                )
            }

            delay(250L)
            when (val result = repository.replayQuiz(quizId)) {
                is QuizGenerationResult.Success -> applySnapshot(
                    snapshot = result.data.snapshot,
                    providerNotice = _uiState.value.providerNotice,
                    resetStreak = true
                )

                QuizGenerationResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            mode = QuizScreenMode.CONFIG,
                            isBusy = false,
                            shouldOpenPaywall = true,
                            errorMessage = app.getString(R.string.quiz_error_requires_premium_replay)
                        )
                    }
                }

                is QuizGenerationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            mode = QuizScreenMode.CONFIG,
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: QuizSnapshot,
        providerNotice: String?,
        resetStreak: Boolean,
        streak: Int = _uiState.value.streak,
        bestStreak: Int = _uiState.value.bestStreak,
        feedback: AnswerFeedback? = null
    ) {
        val mappedQuestions = snapshot.questions.map { question ->
            QuizQuestionUi(
                id = question.id,
                prompt = question.prompt,
                optionA = question.optionA,
                optionB = question.optionB,
                correctAnswer = question.correctAnswer,
                sourceSnippet = question.sourceSnippet,
                userAnswer = question.userAnswer,
                isCorrect = question.isCorrect
            )
        }
        val currentIndex = if (mappedQuestions.isEmpty()) {
            0
        } else {
            val firstUnanswered = mappedQuestions.indexOfFirst { it.userAnswer == null }
            when {
                firstUnanswered >= 0 -> firstUnanswered
                snapshot.quiz.currentIndex in mappedQuestions.indices -> snapshot.quiz.currentIndex
                else -> mappedQuestions.lastIndex
            }
        }

        _uiState.update { state ->
            state.copy(
                documentTitle = snapshot.documentTitle,
                mode = if (snapshot.isCompleted()) QuizScreenMode.RESULT else QuizScreenMode.PLAYING,
                settings = snapshot.quiz.settings.copy(
                    questionCount = snapshot.quiz.settings.questionCount.coerceIn(
                        MIN_QUIZ_QUESTIONS,
                        state.maxQuestionCount
                    )
                ),
                activeQuizId = snapshot.quiz.id,
                questions = mappedQuestions,
                currentQuestionIndex = currentIndex,
                correctCount = snapshot.quiz.correctCount,
                streak = if (resetStreak) 0 else streak,
                bestStreak = if (resetStreak) 0 else bestStreak,
                historyPreview = snapshot.toHistoryPreview(),
                providerNotice = providerNotice,
                answerFeedback = feedback,
                isBusy = false,
                errorMessage = null
            )
        }
    }

    private fun QuizSnapshot.toHistoryPreview(): QuizHistoryPreview {
        val answeredCount = questions.count { it.userAnswer != null }
        return QuizHistoryPreview(
            quizId = quiz.id,
            completed = isCompleted(),
            answeredCount = answeredCount,
            questionCount = questions.size,
            correctCount = quiz.correctCount
        )
    }

    companion object {
        fun factory(
            application: Application,
            documentId: String
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return QuizViewModel(application = application, documentId = documentId) as T
                }
            }
        }
    }
}
