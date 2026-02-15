package com.suraj.apps.omni.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_notes",
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
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["isBookmarked"])
    ]
)
data class StudyNoteEntity(
    @PrimaryKey val id: String,
    val documentId: String?,
    val title: String,
    val content: String,
    val frontContent: String,
    val backContent: String,
    val isBookmarked: Boolean,
    val colorHex: String,
    val createdAtEpochMs: Long
)
