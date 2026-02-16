package com.suraj.apps.omni.core.data.notes

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.provider.PROVIDER_PREFS_NAME
import com.suraj.apps.omni.core.model.DocumentFileType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyNotesRepositoryTest {
    private lateinit var database: OmniDatabase
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext
            .getSharedPreferences("omni_access", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        appContext
            .getSharedPreferences(PROVIDER_PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        database = Room.inMemoryDatabaseBuilder(appContext, OmniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun generateNotesPersistsCardsAndLoadsBootstrap() = runBlocking {
        val documentId = importTextDocument(
            text = "Bitcoin timestamping chains blocks together. " +
                "Proof of work adds resistance against spam. " +
                "Nodes verify signatures before relaying transactions.",
            isPremium = true
        )
        val repository = notesRepository(isPremium = true)

        val generation = repository.generateNotes(documentId, targetCount = 8)

        val success = generation as StudyNotesGenerationResult.Success
        assertEquals(8, success.notes.size)
        assertTrue(success.notes.all { it.frontContent.isNotBlank() })
        assertTrue(success.notes.all { it.backContent.isNotBlank() })

        val bootstrap = repository.loadBootstrap(documentId)
        assertNotNull(bootstrap)
        assertEquals(8, bootstrap?.notes?.size)
    }

    @Test
    fun setBookmarkUpdatesStoredNote() = runBlocking {
        val documentId = importTextDocument(
            text = "Merkle trees summarize transaction sets efficiently.",
            isPremium = true
        )
        val repository = notesRepository(isPremium = true)

        val generated = repository.generateNotes(documentId, targetCount = 4) as StudyNotesGenerationResult.Success
        val firstNote = generated.notes.first()

        val updated = repository.setBookmark(firstNote.id, isBookmarked = true)

        assertNotNull(updated)
        assertTrue(updated?.isBookmarked == true)
    }

    @Test
    fun generateNotesFailsWhenDocumentTextIsMissing() = runBlocking {
        database.documentDao().upsert(
            DocumentEntity(
                id = "doc-no-text",
                title = "No Text",
                createdAtEpochMs = System.currentTimeMillis(),
                fileBookmarkData = null,
                fileType = DocumentFileType.TXT,
                sourceUrl = null,
                extractedTextHash = null,
                extractedTextPreview = "",
                lastOpenedAtEpochMs = null,
                isOnboarding = false,
                onboardingStatus = "ready",
                timeSpentSeconds = 0.0
            )
        )
        val repository = notesRepository(isPremium = true)

        val generation = repository.generateNotes("doc-no-text")

        assertTrue(generation is StudyNotesGenerationResult.Failure)
    }

    private fun notesRepository(isPremium: Boolean): StudyNotesRepository {
        return StudyNotesRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium)
        )
    }

    private suspend fun importTextDocument(text: String, isPremium: Boolean): String {
        val sourceFile = File(appContext.cacheDir, "notes-source-${System.currentTimeMillis()}.txt")
            .apply { writeText(text) }
        val importRepository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium)
        )
        val result = importRepository.importDocument(Uri.fromFile(sourceFile)) as DocumentImportResult.Success
        return result.documentId
    }

    private class FakePremiumAccessChecker(
        private val isPremium: Boolean
    ) : PremiumAccessChecker {
        override fun isPremiumUnlocked(): Boolean = isPremium
    }
}
