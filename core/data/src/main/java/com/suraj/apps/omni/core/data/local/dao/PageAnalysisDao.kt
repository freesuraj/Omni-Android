package com.suraj.apps.omni.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.suraj.apps.omni.core.data.local.entity.PageAnalysisEntity

@Dao
interface PageAnalysisDao {
    @Upsert
    suspend fun upsert(analysis: PageAnalysisEntity)

    @Upsert
    suspend fun upsertAll(analyses: List<PageAnalysisEntity>)

    @Query("SELECT * FROM page_analyses WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getForDocument(documentId: String): List<PageAnalysisEntity>

    @Query("DELETE FROM page_analyses WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM page_analyses")
    suspend fun count(): Int
}
