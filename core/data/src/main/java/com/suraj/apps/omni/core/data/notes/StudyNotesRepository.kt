package com.suraj.apps.omni.core.data.notes

import android.content.Context
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.importing.SharedPrefsPremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.OmniDatabaseFactory
import com.suraj.apps.omni.core.data.local.entity.StudyNoteEntity
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val NOTE_COLORS = listOf(
    "#EFE9B2",
    "#DCE8FF",
    "#FDE6C8",
    "#D7F1E6",
    "#F4E0F8"
)

const val DEFAULT_NOTE_TARGET_COUNT = 16

data class StudyNotesBootstrap(
    val documentTitle: String,
    val notes: List<StudyNoteEntity>
)

sealed interface StudyNotesGenerationResult {
    data class Success(val notes: List<StudyNoteEntity>) : StudyNotesGenerationResult
    data class Failure(val message: String) : StudyNotesGenerationResult
}

class StudyNotesRepository(
    context: Context,
    private val database: OmniDatabase = OmniDatabaseFactory.create(context),
    private val premiumAccessChecker: PremiumAccessChecker = SharedPrefsPremiumAccessChecker(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val importRepository = DocumentImportRepository(
        context = context,
        database = database,
        premiumAccessChecker = premiumAccessChecker,
        ioDispatcher = ioDispatcher
    )

    suspend fun loadBootstrap(documentId: String): StudyNotesBootstrap? = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId) ?: return@withContext null
        val notes = database.studyNoteDao()
            .getForDocument(documentId)
            .sortedBy { it.createdAtEpochMs }
        StudyNotesBootstrap(
            documentTitle = document.title,
            notes = notes
        )
    }

    suspend fun generateNotes(
        documentId: String,
        targetCount: Int = DEFAULT_NOTE_TARGET_COUNT
    ): StudyNotesGenerationResult = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext StudyNotesGenerationResult.Failure("Document not found.")

        val fullText = importRepository.readFullText(documentId).orEmpty().trim()
        if (fullText.isBlank()) {
            return@withContext StudyNotesGenerationResult.Failure(
                "This document is still processing. Try generating notes again shortly."
            )
        }

        val drafts = generateDraftNotes(
            fullText = fullText,
            targetCount = targetCount.coerceIn(4, 24)
        )

        if (drafts.isEmpty()) {
            return@withContext StudyNotesGenerationResult.Failure(
                "Could not extract enough content to generate flashcards."
            )
        }

        database.studyNoteDao().deleteForDocument(documentId)
        val now = System.currentTimeMillis()
        val notes = drafts.mapIndexed { index, draft ->
            StudyNoteEntity(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                title = draft.front,
                content = draft.back,
                frontContent = draft.front,
                backContent = draft.back,
                isBookmarked = false,
                colorHex = NOTE_COLORS[index % NOTE_COLORS.size],
                createdAtEpochMs = now + index
            )
        }

        database.studyNoteDao().upsertAll(notes)
        val persisted = database.studyNoteDao()
            .getForDocument(documentId)
            .sortedBy { it.createdAtEpochMs }

        StudyNotesGenerationResult.Success(persisted)
    }

    suspend fun setBookmark(
        noteId: String,
        isBookmarked: Boolean
    ): StudyNoteEntity? = withContext(ioDispatcher) {
        database.studyNoteDao().updateBookmark(noteId, isBookmarked)
        database.studyNoteDao().getById(noteId)
    }
}

private data class NoteDraft(
    val front: String,
    val back: String
)

private fun generateDraftNotes(
    fullText: String,
    targetCount: Int
): List<NoteDraft> {
    val sentences = fullText
        .replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.length >= 18 }

    if (sentences.isEmpty()) return emptyList()

    val chunks = sentences.chunked(2)
    return List(targetCount) { index ->
        val chunk = chunks[index % chunks.size]
        val leadSentence = chunk.first()
        val question = buildFrontPrompt(leadSentence)
        val answer = chunk.joinToString(" ")

        NoteDraft(
            front = question,
            back = answer
        )
    }
}

private fun buildFrontPrompt(sentence: String): String {
    if (sentence.endsWith("?")) return sentence

    val tokens = sentence
        .split(Regex("[^A-Za-z0-9]+"))
        .map { it.trim() }
        .filter { it.length >= 5 }

    val keyword = tokens.maxByOrNull { it.length }
    return if (keyword == null) {
        "What key idea is described in this note?"
    } else {
        "How does $keyword relate to this document?"
    }
}
