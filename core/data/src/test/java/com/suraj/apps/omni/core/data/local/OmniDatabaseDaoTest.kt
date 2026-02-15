package com.suraj.apps.omni.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.local.entity.DocumentSummaryEntity
import com.suraj.apps.omni.core.data.local.entity.PageAnalysisEntity
import com.suraj.apps.omni.core.data.local.entity.QaMessageEntity
import com.suraj.apps.omni.core.data.local.entity.QuestionEntity
import com.suraj.apps.omni.core.data.local.entity.QuizEntity
import com.suraj.apps.omni.core.data.local.entity.StudyNoteEntity
import com.suraj.apps.omni.core.model.DocumentFileType
import com.suraj.apps.omni.core.model.QuestionStatus
import com.suraj.apps.omni.core.model.QuizDifficulty
import com.suraj.apps.omni.core.model.QuizSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OmniDatabaseDaoTest {
    private lateinit var database: OmniDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OmniDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun documentAggregateLoadsNestedRelations() = runBlocking {
        val documentId = "doc-1"
        val quizA = "quiz-a"
        val quizB = "quiz-b"
        seedDocumentGraph(documentId = documentId, firstQuizId = quizA, secondQuizId = quizB)

        val aggregate = database.documentDao().getAggregateById(documentId)
        assertNotNull(aggregate)
        assertEquals(2, aggregate?.quizzes?.size)
        assertEquals(1, aggregate?.studyNotes?.size)
        assertEquals(1, aggregate?.summaries?.size)
        assertEquals(1, aggregate?.qaMessages?.size)
        assertEquals(1, aggregate?.pageAnalyses?.size)

        val questionsByQuiz = aggregate?.quizzes?.associate { it.quiz.id to it.questions.size }.orEmpty()
        assertEquals(2, questionsByQuiz[quizA])
        assertEquals(1, questionsByQuiz[quizB])
    }

    @Test
    fun deletingDocumentCascadesToAllChildren() = runBlocking {
        val documentId = "doc-2"
        seedDocumentGraph(documentId = documentId, firstQuizId = "quiz-main", secondQuizId = "quiz-side")

        database.documentDao().deleteById(documentId)

        assertEquals(0, database.documentDao().count())
        assertEquals(0, database.quizDao().countQuizzes())
        assertEquals(0, database.quizDao().countQuestions())
        assertEquals(0, database.studyNoteDao().count())
        assertEquals(0, database.documentSummaryDao().count())
        assertEquals(0, database.qaMessageDao().count())
        assertEquals(0, database.pageAnalysisDao().count())
    }

    @Test
    fun deletingQuizCascadesToQuestionsOnly() = runBlocking {
        val documentId = "doc-3"
        val quizId = "quiz-delete"
        seedDocument(documentId)
        database.quizDao().upsertQuiz(
            QuizEntity(
                id = quizId,
                documentId = documentId,
                createdAtEpochMs = 1_700_000_010_000,
                settings = QuizSettings(questionCount = 8, difficulty = QuizDifficulty.HARD),
                currentIndex = 0,
                correctCount = 0,
                completedAtEpochMs = null,
                isReview = false
            )
        )
        database.quizDao().upsertQuestions(
            listOf(
                QuestionEntity(
                    id = "question-delete-1",
                    quizId = quizId,
                    prompt = "Prompt 1",
                    optionA = "A",
                    optionB = "B",
                    correctAnswer = "A",
                    sourceSnippet = null,
                    userAnswer = null,
                    isCorrect = null,
                    createdFromChunkIndex = 0,
                    previousStatus = QuestionStatus.NEW
                ),
                QuestionEntity(
                    id = "question-delete-2",
                    quizId = quizId,
                    prompt = "Prompt 2",
                    optionA = "A",
                    optionB = "B",
                    correctAnswer = "B",
                    sourceSnippet = null,
                    userAnswer = null,
                    isCorrect = null,
                    createdFromChunkIndex = 1,
                    previousStatus = QuestionStatus.NEW
                )
            )
        )

        database.quizDao().deleteQuizById(quizId)

        assertEquals(1, database.documentDao().count())
        assertEquals(0, database.quizDao().countQuizzes())
        assertEquals(0, database.quizDao().countQuestions())
    }

    private suspend fun seedDocumentGraph(
        documentId: String,
        firstQuizId: String,
        secondQuizId: String
    ) {
        seedDocument(documentId)
        database.quizDao().upsertQuizzes(
            listOf(
                QuizEntity(
                    id = firstQuizId,
                    documentId = documentId,
                    createdAtEpochMs = 1_700_000_001_000,
                    settings = QuizSettings(questionCount = 10, difficulty = QuizDifficulty.MEDIUM),
                    currentIndex = 3,
                    correctCount = 2,
                    completedAtEpochMs = null,
                    isReview = false
                ),
                QuizEntity(
                    id = secondQuizId,
                    documentId = documentId,
                    createdAtEpochMs = 1_700_000_002_000,
                    settings = QuizSettings(questionCount = 5, difficulty = QuizDifficulty.EASY),
                    currentIndex = 0,
                    correctCount = 0,
                    completedAtEpochMs = null,
                    isReview = true
                )
            )
        )
        database.quizDao().upsertQuestions(
            listOf(
                QuestionEntity(
                    id = "question-1",
                    quizId = firstQuizId,
                    prompt = "Question 1",
                    optionA = "A",
                    optionB = "B",
                    correctAnswer = "A",
                    sourceSnippet = "Source 1",
                    userAnswer = null,
                    isCorrect = null,
                    createdFromChunkIndex = 1,
                    previousStatus = QuestionStatus.NEW
                ),
                QuestionEntity(
                    id = "question-2",
                    quizId = firstQuizId,
                    prompt = "Question 2",
                    optionA = "A",
                    optionB = "B",
                    correctAnswer = "B",
                    sourceSnippet = "Source 2",
                    userAnswer = "A",
                    isCorrect = false,
                    createdFromChunkIndex = 2,
                    previousStatus = QuestionStatus.INCORRECT
                ),
                QuestionEntity(
                    id = "question-3",
                    quizId = secondQuizId,
                    prompt = "Question 3",
                    optionA = "A",
                    optionB = "B",
                    correctAnswer = "A",
                    sourceSnippet = null,
                    userAnswer = null,
                    isCorrect = null,
                    createdFromChunkIndex = null,
                    previousStatus = null
                )
            )
        )
        database.studyNoteDao().upsert(
            StudyNoteEntity(
                id = "note-1",
                documentId = documentId,
                title = "Legacy title",
                content = "Legacy content",
                frontContent = "Front",
                backContent = "Back",
                isBookmarked = true,
                colorHex = "#F6D365",
                createdAtEpochMs = 1_700_000_003_000
            )
        )
        database.documentSummaryDao().upsert(
            DocumentSummaryEntity(
                id = "summary-1",
                documentId = documentId,
                content = "Summary content",
                wordCount = 2,
                createdAtEpochMs = 1_700_000_004_000
            )
        )
        database.qaMessageDao().upsert(
            QaMessageEntity(
                id = "message-1",
                documentId = documentId,
                content = "What is this document about?",
                isUser = true,
                isError = false,
                createdAtEpochMs = 1_700_000_005_000
            )
        )
        database.pageAnalysisDao().upsert(
            PageAnalysisEntity(
                id = "analysis-1",
                documentId = documentId,
                pageNumber = 1,
                content = "Detailed analysis",
                thumbnailData = byteArrayOf(0x1, 0x2, 0x3),
                createdAtEpochMs = 1_700_000_006_000
            )
        )
    }

    private suspend fun seedDocument(documentId: String) {
        database.documentDao().upsert(
            DocumentEntity(
                id = documentId,
                title = "Sample document",
                createdAtEpochMs = 1_700_000_000_000,
                fileBookmarkData = null,
                fileType = DocumentFileType.PDF,
                sourceUrl = "https://example.com/article",
                extractedTextHash = "hash",
                extractedTextPreview = "Preview text",
                lastOpenedAtEpochMs = null,
                isOnboarding = true,
                onboardingStatus = "processing",
                timeSpentSeconds = 120.0
            )
        )
    }
}
