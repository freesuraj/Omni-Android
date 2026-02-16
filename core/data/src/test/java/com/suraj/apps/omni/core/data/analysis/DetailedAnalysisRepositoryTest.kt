package com.suraj.apps.omni.core.data.analysis

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
class DetailedAnalysisRepositoryTest {
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
    fun generateAnalysisRequiresPremiumWhenLocked() = runBlocking {
        val documentId = importTextDocument(
            text = "Bitcoin uses timestamped blocks to coordinate a shared transaction history.",
            isPremium = false
        )
        val repository = analysisRepository(isPremium = false)

        val result = repository.generateAnalysis(documentId = documentId, retryFromScratch = false)

        assertTrue(result is DetailedAnalysisGenerationResult.RequiresPremium)
    }

    @Test
    fun generateAnalysisPersistsSectionRecords() = runBlocking {
        val documentId = importTextDocument(
            text = longText(wordCount = 420),
            isPremium = true
        )
        val repository = analysisRepository(isPremium = true)

        val result = repository.generateAnalysis(documentId = documentId, retryFromScratch = false)

        val success = result as DetailedAnalysisGenerationResult.Success
        assertTrue(success.expectedAnalysisCount >= 2)
        assertEquals(success.expectedAnalysisCount, success.analyses.size)
        assertEquals(success.expectedAnalysisCount, database.pageAnalysisDao().count())
    }

    @Test
    fun resumeGenerationSkipsSavedSectionsAndCompletesMissingOnes() = runBlocking {
        val documentId = importTextDocument(
            text = longText(wordCount = 390),
            isPremium = true
        )
        val repository = analysisRepository(isPremium = true)

        val initial = repository.generateAnalysis(documentId = documentId, retryFromScratch = false)
            as DetailedAnalysisGenerationResult.Success

        val savedFirstSection = initial.analyses.first()
        database.pageAnalysisDao().deleteForDocument(documentId)
        database.pageAnalysisDao().upsert(savedFirstSection)

        val resumed = repository.generateAnalysis(documentId = documentId, retryFromScratch = false)
            as DetailedAnalysisGenerationResult.Success

        assertEquals(initial.expectedAnalysisCount, resumed.analyses.size)
        assertEquals(initial.expectedAnalysisCount, database.pageAnalysisDao().count())

        val bootstrap = repository.loadBootstrap(documentId)
        assertNotNull(bootstrap)
        assertEquals(initial.expectedAnalysisCount, bootstrap?.expectedAnalysisCount)
    }

    private fun analysisRepository(isPremium: Boolean): DetailedAnalysisRepository {
        return DetailedAnalysisRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium)
        )
    }

    private suspend fun importTextDocument(text: String, isPremium: Boolean): String {
        val sourceFile = File(appContext.cacheDir, "analysis-source-${System.currentTimeMillis()}.txt")
            .apply { writeText(text) }
        val importRepository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium)
        )
        val result = importRepository.importDocument(Uri.fromFile(sourceFile)) as DocumentImportResult.Success
        return result.documentId
    }

    private fun longText(wordCount: Int): String {
        val tokenPool = listOf(
            "bitcoin",
            "network",
            "consensus",
            "timestamp",
            "block",
            "transaction",
            "merkle",
            "signature",
            "peer",
            "verification"
        )
        return (0 until wordCount)
            .joinToString(" ") { index -> tokenPool[index % tokenPool.size] }
    }

    private class FakePremiumAccessChecker(
        private val isPremium: Boolean
    ) : PremiumAccessChecker {
        override fun isPremiumUnlocked(): Boolean = isPremium
    }
}
