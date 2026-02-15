package com.suraj.apps.omni.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.suraj.apps.omni.core.data.local.entity.DocumentSummaryEntity

@Dao
interface DocumentSummaryDao {
    @Upsert
    suspend fun upsert(summary: DocumentSummaryEntity)

    @Upsert
    suspend fun upsertAll(summaries: List<DocumentSummaryEntity>)

    @Query("SELECT * FROM document_summaries WHERE documentId = :documentId ORDER BY createdAtEpochMs DESC")
    suspend fun getForDocument(documentId: String): List<DocumentSummaryEntity>

    @Query("SELECT COUNT(*) FROM document_summaries")
    suspend fun count(): Int
}
