package com.suraj.apps.omni.core.data.importing

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
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
class DocumentImportRepositoryTest {
    private lateinit var database: OmniDatabase
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(appContext, OmniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importTextFileCopiesSourceAndPersistsFullTextArtifact() = runBlocking {
        val sourceText = "Omni captures text from imported files."
        val sourceFile = File(appContext.cacheDir, "sample-import.txt").apply { writeText(sourceText) }
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = false)
        )

        val result = repository.importDocument(Uri.fromFile(sourceFile))

        val success = result as DocumentImportResult.Success
        val imported = database.documentDao().getById(success.documentId)
        assertNotNull(imported)
        assertEquals(DocumentFileType.TXT, imported?.fileType)
        assertTrue((imported?.extractedTextPreview ?: "").contains("Omni"))

        val copiedPath = imported?.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
        val copiedFile = File(copiedPath)
        assertTrue(copiedFile.exists())
        assertEquals(sourceText, copiedFile.readText())

        val fullText = repository.readFullText(success.documentId)
        assertEquals(sourceText, fullText)
    }

    @Test
    fun importBlockedForNonPremiumAfterOneDocument() = runBlocking {
        database.documentDao().upsert(
            DocumentEntity(
                id = "existing-doc",
                title = "Existing",
                createdAtEpochMs = System.currentTimeMillis(),
                fileBookmarkData = null,
                fileType = DocumentFileType.PDF,
                sourceUrl = null,
                extractedTextHash = null,
                extractedTextPreview = null,
                lastOpenedAtEpochMs = null,
                isOnboarding = false,
                onboardingStatus = null,
                timeSpentSeconds = 0.0
            )
        )
        val sourceFile = File(appContext.cacheDir, "blocked-import.txt").apply { writeText("data") }
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = false)
        )

        val result = repository.importDocument(Uri.fromFile(sourceFile))

        assertTrue(result is DocumentImportResult.RequiresPremium)
    }

    private class FakePremiumAccessChecker(
        private val isPremium: Boolean
    ) : PremiumAccessChecker {
        override fun isPremiumUnlocked(): Boolean = isPremium
    }
}
