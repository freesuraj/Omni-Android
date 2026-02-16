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

    @Query("SELECT * FROM study_notes WHERE id = :noteId LIMIT 1")
    suspend fun getById(noteId: String): StudyNoteEntity?

    @Query("SELECT * FROM study_notes WHERE documentId = :documentId ORDER BY createdAtEpochMs DESC")
    suspend fun getForDocument(documentId: String): List<StudyNoteEntity>

    @Query("UPDATE study_notes SET isBookmarked = :isBookmarked WHERE id = :noteId")
    suspend fun updateBookmark(noteId: String, isBookmarked: Boolean)

    @Query("DELETE FROM study_notes WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("SELECT COUNT(*) FROM study_notes")
    suspend fun count(): Int
}
