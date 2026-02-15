package com.suraj.apps.omni.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.suraj.apps.omni.core.model.DocumentFileType

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["fileType"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val fileBookmarkData: ByteArray?,
    val fileType: DocumentFileType,
    val sourceUrl: String?,
    val extractedTextHash: String?,
    val extractedTextPreview: String?,
    val lastOpenedAtEpochMs: Long?,
    val isOnboarding: Boolean,
    val onboardingStatus: String?,
    val timeSpentSeconds: Double
)
