package com.suraj.apps.omni.core.data.qa

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.model.DocumentFileType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QaRepositoryTest {
    private lateinit var database: OmniDatabase
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext
            .getSharedPreferences("omni_access", Context.MODE_PRIVATE)
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
    fun askQuestionPersistsUserAndAssistantMessages() = runBlocking {
        val documentId = importTextDocument(
            text = "Bitcoin prevents double spending without trusted intermediaries. " +
                "Proof of work orders blocks over time. Nodes verify signatures and balances.",
            isPremium = true
        )
        val repository = qaRepository(isPremium = true)

        val result = repository.askQuestion(
            documentId = documentId,
            question = "How does Bitcoin prevent double spending?"
        )

        val success = result as QaAskResult.Success
        assertTrue(success.userMessage.isUser)
        assertFalse(success.assistantMessage.isUser)
        assertFalse(success.assistantMessage.isError)

        val stored = database.qaMessageDao().getForDocument(documentId)
        assertEquals(2, stored.size)
        assertTrue(stored.first().isUser)
        assertFalse(stored.last().isUser)
    }

    @Test
    fun askQuestionReturnsRequiresPremiumForFreeTier() = runBlocking {
        val documentId = importTextDocument(
            text = "Merkle trees provide efficient inclusion proofs.",
            isPremium = false
        )
        val repository = qaRepository(isPremium = false)

        val result = repository.askQuestion(
            documentId = documentId,
            question = "What is a Merkle tree?"
        )

        assertTrue(result is QaAskResult.RequiresPremium)
        assertEquals(0, database.qaMessageDao().count())
    }

    @Test
    fun askQuestionPersistsErrorAssistantWhenSourceTextMissing() = runBlocking {
        val documentId = "qa-empty-source"
        database.documentDao().upsert(
            DocumentEntity(
                id = documentId,
                title = "Untitled",
                createdAtEpochMs = System.currentTimeMillis(),
                fileBookmarkData = null,
                fileType = DocumentFileType.PDF,
                sourceUrl = null,
                extractedTextHash = null,
                extractedTextPreview = null,
                lastOpenedAtEpochMs = null,
                isOnboarding = true,
                onboardingStatus = "imported",
                timeSpentSeconds = 0.0
            )
        )

        val repository = qaRepository(isPremium = true)
        val result = repository.askQuestion(documentId = documentId, question = "Any update?")

        val success = result as QaAskResult.Success
        assertTrue(success.assistantMessage.isError)

        val stored = database.qaMessageDao().getForDocument(documentId)
        assertEquals(2, stored.size)
        assertTrue(stored.last().isError)
    }

    @Test
    fun loadBootstrapReturnsHistoryAndDocumentTitle() = runBlocking {
        val documentId = importTextDocument(
            text = "Difficulty adjustment helps keep average block interval stable.",
            isPremium = true
        )
        val repository = qaRepository(isPremium = true)

        repository.askQuestion(documentId, "What does difficulty adjustment do?")
        val bootstrap = repository.loadBootstrap(documentId)

        assertNotNull(bootstrap)
        assertTrue(bootstrap?.documentTitle?.isNotBlank() == true)
        assertTrue((bootstrap?.messages?.size ?: 0) >= 2)
        assertTrue(bootstrap?.isPremiumUnlocked == true)
    }

    private fun qaRepository(isPremium: Boolean): QaRepository {
        return QaRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium)
        )
    }

    private suspend fun importTextDocument(text: String, isPremium: Boolean): String {
        val sourceFile = File(appContext.cacheDir, "qa-source-${System.currentTimeMillis()}.txt")
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
