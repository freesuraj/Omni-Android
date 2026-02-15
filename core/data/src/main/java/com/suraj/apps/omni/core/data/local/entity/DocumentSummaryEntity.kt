package com.suraj.apps.omni.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_summaries",
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
data class DocumentSummaryEntity(
    @PrimaryKey val id: String,
    val documentId: String?,
    val content: String,
    val wordCount: Int,
    val createdAtEpochMs: Long
)
