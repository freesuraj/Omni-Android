package com.suraj.apps.omni.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.suraj.apps.omni.core.data.local.entity.QuestionEntity
import com.suraj.apps.omni.core.data.local.entity.QuizEntity
import com.suraj.apps.omni.core.data.local.model.QuizWithQuestions
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {
    @Upsert
    suspend fun upsertQuiz(quiz: QuizEntity)

    @Upsert
    suspend fun upsertQuizzes(quizzes: List<QuizEntity>)

    @Upsert
    suspend fun upsertQuestion(question: QuestionEntity)

    @Upsert
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Transaction
    @Query("SELECT * FROM quizzes WHERE id = :quizId LIMIT 1")
    suspend fun getQuizWithQuestions(quizId: String): QuizWithQuestions?

    @Transaction
    @Query("SELECT * FROM quizzes WHERE documentId = :documentId ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getLatestQuizWithQuestions(documentId: String): QuizWithQuestions?

    @Query("SELECT * FROM quizzes WHERE documentId = :documentId ORDER BY createdAtEpochMs DESC")
    suspend fun getForDocument(documentId: String): List<QuizEntity>

    @Query("DELETE FROM quizzes WHERE id = :quizId")
    suspend fun deleteQuizById(quizId: String)

    @Query("SELECT COUNT(*) FROM quizzes")
    suspend fun countQuizzes(): Int

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun countQuestions(): Int

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE quizId = (
            SELECT id FROM quizzes
            WHERE documentId = :documentId
            ORDER BY createdAtEpochMs DESC
            LIMIT 1
        )
        """
    )
    fun observeLatestQuizQuestionCount(documentId: String): Flow<Int>
}
