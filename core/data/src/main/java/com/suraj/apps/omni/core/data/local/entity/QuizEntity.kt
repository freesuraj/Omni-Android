package com.suraj.apps.omni.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.suraj.apps.omni.core.model.QuizSettings

@Entity(
    tableName = "quizzes",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["createdAtEpochMs"])
    ]
)
data class QuizEntity(
    @PrimaryKey val id: String,
    val documentId: String?,
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "settingsJson")
    val settings: QuizSettings,
    val currentIndex: Int,
    val correctCount: Int,
    val completedAtEpochMs: Long?,
    val isReview: Boolean
)
