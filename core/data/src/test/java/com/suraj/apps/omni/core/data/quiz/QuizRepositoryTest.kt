package com.suraj.apps.omni.core.data.quiz

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.model.QuizDifficulty
import com.suraj.apps.omni.core.model.QuizSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuizRepositoryTest {
    private lateinit var database: OmniDatabase
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext
            .getSharedPreferences("omni_access", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        database = Room.inMemoryDatabaseBuilder(appContext, OmniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun generateQuizPersistsQuestionsAndSupportsAnswerFlow() = runBlocking {
        val documentId = importTextDocument(
            text = "Bitcoin uses a peer-to-peer network to avoid double spending. " +
                "Proof of work secures the chain by requiring computational effort.",
            isPremium = true
        )
        val repository = quizRepository(isPremium = true)

        val generate = repository.generateQuiz(
            documentId = documentId,
            requestedSettings = QuizSettings(
                questionCount = 6,
                difficulty = QuizDifficulty.MEDIUM,
                showSourceSnippet = true,
                soundsEnabled = true
            )
        )

        val success = generate as QuizGenerationResult.Success
        assertEquals(6, success.data.snapshot.questions.size)
        assertEquals(1, database.quizDao().countQuizzes())
        assertEquals(6, database.quizDao().countQuestions())

        var activeSnapshot = success.data.snapshot
        activeSnapshot.questions.forEach { question ->
            val answer = question.correctAnswer
            val answerResult = repository.answerQuestion(activeSnapshot.quiz.id, question.id, answer)
            assertNotNull(answerResult)
            activeSnapshot = answerResult?.snapshot ?: activeSnapshot
        }

        assertTrue(activeSnapshot.isCompleted())
        assertEquals(6, activeSnapshot.quiz.correctCount)
        assertNotNull(activeSnapshot.quiz.completedAtEpochMs)
    }

    @Test
    fun freeTierBlocksQuestionCountAboveLimit() = runBlocking {
        val documentId = importTextDocument(
            text = "Neural models summarize source text into smaller chunks for review.",
            isPremium = false
        )
        val repository = quizRepository(isPremium = false)

        val result = repository.generateQuiz(
            documentId = documentId,
            requestedSettings = QuizSettings(questionCount = FREE_MAX_QUIZ_QUESTIONS + 2)
        )

        assertTrue(result is QuizGenerationResult.RequiresPremium)
    }

    @Test
    fun replayQuizCreatesFreshSessionWithResetAnswers() = runBlocking {
        val documentId = importTextDocument(
            text = "A Merkle tree hashes transactions pairwise to support efficient verification.",
            isPremium = true
        )
        val repository = quizRepository(isPremium = true)

        val generated = repository.generateQuiz(
            documentId = documentId,
            requestedSettings = QuizSettings(questionCount = MIN_QUIZ_QUESTIONS)
        ) as QuizGenerationResult.Success

        val firstQuestion = generated.data.snapshot.questions.first()
        repository.answerQuestion(
            quizId = generated.data.snapshot.quiz.id,
            questionId = firstQuestion.id,
            selectedAnswer = "A"
        )

        val replayed = repository.replayQuiz(generated.data.snapshot.quiz.id) as QuizGenerationResult.Success

        assertNotEquals(generated.data.snapshot.quiz.id, replayed.data.snapshot.quiz.id)
        assertEquals(MIN_QUIZ_QUESTIONS, replayed.data.snapshot.questions.size)
        assertTrue(replayed.data.snapshot.questions.all { it.userAnswer == null })
        assertEquals(2, database.quizDao().countQuizzes())
        assertEquals(MIN_QUIZ_QUESTIONS * 2, database.quizDao().countQuestions())
    }

    private fun quizRepository(isPremium: Boolean): QuizRepository {
        return QuizRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = isPremium)
        )
    }

    private suspend fun importTextDocument(text: String, isPremium: Boolean): String {
        val sourceFile = File(appContext.cacheDir, "quiz-source-${System.currentTimeMillis()}.txt")
            .apply { writeText(text) }
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = isPremium)
        )
        val result = repository.importDocument(Uri.fromFile(sourceFile)) as DocumentImportResult.Success
        return result.documentId
    }

    private class FakePremiumAccessChecker(
        private val isPremium: Boolean
    ) : PremiumAccessChecker {
        override fun isPremiumUnlocked(): Boolean = isPremium
    }
}
