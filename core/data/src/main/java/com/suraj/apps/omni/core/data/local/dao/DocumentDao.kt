package com.suraj.apps.omni.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.local.model.DocumentAggregate
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Upsert
    suspend fun upsert(document: DocumentEntity)

    @Upsert
    suspend fun upsertAll(documents: List<DocumentEntity>)

    @Query("SELECT * FROM documents ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    fun observeById(documentId: String): Flow<DocumentEntity?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getAggregateById(documentId: String): DocumentAggregate?

    @Query("UPDATE documents SET title = :title WHERE id = :documentId")
    suspend fun rename(documentId: String, title: String)

    @Query(
        "UPDATE documents " +
            "SET timeSpentSeconds = timeSpentSeconds + :deltaSeconds, " +
            "lastOpenedAtEpochMs = :openedAtEpochMs " +
            "WHERE id = :documentId"
    )
    suspend fun addReadingTime(
        documentId: String,
        deltaSeconds: Double,
        openedAtEpochMs: Long
    )

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int
}
