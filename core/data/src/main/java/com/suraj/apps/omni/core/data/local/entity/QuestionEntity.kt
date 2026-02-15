package com.suraj.apps.omni.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.suraj.apps.omni.core.model.QuestionStatus

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuizEntity::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["quizId"])]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val quizId: String,
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val correctAnswer: String,
    val sourceSnippet: String?,
    val userAnswer: String?,
    val isCorrect: Boolean?,
    val createdFromChunkIndex: Int?,
    val previousStatus: QuestionStatus?
)
