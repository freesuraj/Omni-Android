package com.suraj.apps.omni.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.suraj.apps.omni.core.data.local.entity.StudyNoteEntity

@Dao
interface StudyNoteDao {
    @Upsert
    suspend fun upsert(note: StudyNoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<StudyNoteEntity>)

    @Query("SELECT * FROM study_notes WHERE documentId = :documentId ORDER BY createdAtEpochMs DESC")
    suspend fun getForDocument(documentId: String): List<StudyNoteEntity>

    @Query("SELECT COUNT(*) FROM study_notes")
    suspend fun count(): Int
}
