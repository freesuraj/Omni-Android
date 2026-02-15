package com.suraj.apps.omni.core.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.suraj.apps.omni.core.data.local.entity.QuestionEntity
import com.suraj.apps.omni.core.data.local.entity.QuizEntity

data class QuizWithQuestions(
    @Embedded val quiz: QuizEntity,
    @Relation(parentColumn = "id", entityColumn = "quizId")
    val questions: List<QuestionEntity>
)
