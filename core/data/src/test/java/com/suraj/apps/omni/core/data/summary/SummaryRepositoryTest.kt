package com.suraj.apps.omni.core.data.summary

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.provider.PROVIDER_PREFS_NAME
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
class SummaryRepositoryTest {
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
    fun generateSummaryPersistsAndReturnsStoredEntity() = runBlocking {
        val documentId = importTextDocument(
            text = "Bitcoin enables peer-to-peer value transfer. " +
                "Proof of work helps secure consensus. " +
                "Nodes validate and relay transactions.",
            isPremium = true
        )
        val repository = summaryRepository(isPremium = true)

        val result = repository.generateSummary(documentId = documentId, targetWordCount = 120)

        val success = result as SummaryGenerationResult.Success
        assertTrue(success.summary.content.isNotBlank())
        assertTrue(success.summary.wordCount <= 120)
        assertEquals(1, database.documentSummaryDao().count())
    }

    @Test
    fun freeTierRequiresPremiumForLongSummaryTarget() = runBlocking {
        val documentId = importTextDocument(
            text = "Summaries can be tuned by target word count for readability and depth.",
            isPremium = false
        )
        val repository = summaryRepository(isPremium = false)

        val result = repository.generateSummary(
            documentId = documentId,
            targetWordCount = FREE_MAX_SUMMARY_WORDS + 50
        )

        assertTrue(result is SummaryGenerationResult.RequiresPremium)
    }

    @Test
    fun loadBootstrapReturnsLatestSummariesForDocument() = runBlocking {
        val documentId = importTextDocument(
            text = "Merkle trees summarize transaction sets. Timestamp chains maintain ordering.",
            isPremium = true
        )
        val repository = summaryRepository(isPremium = true)

        repository.generateSummary(documentId, 90)
        repository.generateSummary(documentId, 100)

        val bootstrap = repository.loadBootstrap(documentId)

        assertNotNull(bootstrap)
        assertEquals(2, bootstrap?.summaries?.size)
        assertTrue((bootstrap?.summaries?.first()?.createdAtEpochMs ?: 0L) >= (bootstrap?.summaries?.last()?.createdAtEpochMs ?: 0L))
    }

    private fun summaryRepository(isPremium: Boolean): SummaryRepository {
        return SummaryRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium)
        )
    }

    private suspend fun importTextDocument(text: String, isPremium: Boolean): String {
        val sourceFile = File(appContext.cacheDir, "summary-source-${System.currentTimeMillis()}.txt")
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
