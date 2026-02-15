package com.suraj.apps.omni.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.suraj.apps.omni.core.data.local.entity.QaMessageEntity

@Dao
interface QaMessageDao {
    @Upsert
    suspend fun upsert(message: QaMessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<QaMessageEntity>)

    @Query("SELECT * FROM qa_messages WHERE documentId = :documentId ORDER BY createdAtEpochMs ASC")
    suspend fun getForDocument(documentId: String): List<QaMessageEntity>

    @Query("SELECT COUNT(*) FROM qa_messages")
    suspend fun count(): Int
}
