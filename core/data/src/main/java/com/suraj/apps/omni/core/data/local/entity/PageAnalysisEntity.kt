package com.suraj.apps.omni.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_analyses",
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
        Index(value = ["documentId", "pageNumber"], unique = true)
    ]
)
data class PageAnalysisEntity(
    @PrimaryKey val id: String,
    val documentId: String?,
    val pageNumber: Int,
    val content: String,
    val thumbnailData: ByteArray?,
    val createdAtEpochMs: Long
)
