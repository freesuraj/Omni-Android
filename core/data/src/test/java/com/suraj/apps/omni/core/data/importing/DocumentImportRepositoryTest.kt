package com.suraj.apps.omni.core.data.importing

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionEngine
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionProgress
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionResult
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
class DocumentImportRepositoryTest {
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
    fun importPdfExtractsFullDocumentTextIntoArtifact() = runBlocking {
        val sourceFile = File(appContext.cacheDir, "sample-import.pdf")
        buildPdf(
            file = sourceFile,
            lines = listOf(
                "Bitcoin prevents double spending without trusted intermediaries.",
                "Nodes validate transactions and propagate them across the network."
            )
        )
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = false)
        )

        val result = repository.importDocument(Uri.fromFile(sourceFile))

        val success = result as DocumentImportResult.Success
        val fullText = repository.readFullText(success.documentId).orEmpty()
        assertTrue(fullText.contains("Bitcoin prevents double spending"))
        assertTrue(fullText.contains("Nodes validate transactions"))
        assertFalse(fullText.equals("PDF imported. Text extraction runs in onboarding.", ignoreCase = true))
        assertFalse(fullText.equals("PDF imported. Unable to extract readable text.", ignoreCase = true))
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

    @Test
    fun importLiveRecordingPersistsTranscriptAndTracksFreeQuota() = runBlocking {
        val sourceAudio = File(appContext.cacheDir, "live-source.m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val transcript = "Live transcript from microphone session."
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = false)
        )

        val result = repository.importLiveRecording(sourceAudioFile = sourceAudio, transcript = transcript)

        val success = result as DocumentImportResult.Success
        val imported = database.documentDao().getById(success.documentId)
        assertNotNull(imported)
        assertEquals(DocumentFileType.AUDIO, imported?.fileType)
        assertTrue((imported?.extractedTextPreview ?: "").contains("Live transcript"))
        assertEquals(transcript, repository.readFullText(success.documentId))
        assertEquals(1, repository.remainingFreeLiveRecordings())
    }

    @Test
    fun importLiveRecordingRequiresPremiumAfterTwoLifetimeSessions() = runBlocking {
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = false)
        )
        repeat(2) { index ->
            val sourceAudio = File(appContext.cacheDir, "live-source-$index.m4a").apply {
                writeBytes(byteArrayOf(index.toByte()))
            }
            val result = repository.importLiveRecording(
                sourceAudioFile = sourceAudio,
                transcript = "Transcript $index"
            )
            assertTrue(result is DocumentImportResult.Success)
        }

        val blockedResult = repository.importLiveRecording(
            sourceAudioFile = File(appContext.cacheDir, "live-source-blocked.m4a").apply {
                writeBytes(byteArrayOf(9))
            },
            transcript = "Blocked transcript"
        )

        assertTrue(blockedResult is DocumentImportResult.RequiresPremium)
        assertEquals(0, repository.remainingFreeLiveRecordings())
    }

    @Test
    fun renameDocumentUpdatesStoredTitle() = runBlocking {
        val sourceText = File(appContext.cacheDir, "rename-source.txt").apply {
            writeText("Rename me")
        }
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = true)
        )
        val importResult = repository.importDocument(Uri.fromFile(sourceText)) as DocumentImportResult.Success

        val renameResult = repository.renameDocument(importResult.documentId, " Updated   Title ")

        assertTrue(renameResult is DocumentImportResult.Success)
        val updated = database.documentDao().getById(importResult.documentId)
        assertEquals("Updated Title", updated?.title)
    }

    @Test
    fun deleteDocumentRemovesDatabaseEntryAndArtifacts() = runBlocking {
        val sourceText = File(appContext.cacheDir, "delete-source.txt").apply {
            writeText("Delete me")
        }
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = true)
        )
        val importResult = repository.importDocument(Uri.fromFile(sourceText)) as DocumentImportResult.Success
        val imported = database.documentDao().getById(importResult.documentId)
        val importedFilePath = imported?.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
        val importedFile = File(importedFilePath)
        assertTrue(importedFile.exists())
        assertNotNull(repository.readFullText(importResult.documentId))

        val deleteResult = repository.deleteDocument(importResult.documentId)

        assertTrue(deleteResult is DocumentImportResult.Success)
        assertEquals(null, database.documentDao().getById(importResult.documentId))
        assertEquals(null, repository.readFullText(importResult.documentId))
        assertTrue(!importedFile.exists())
    }

    @Test
    fun transcribeAudioDocumentPersistsTranscriptAndMarksDocumentReady() = runBlocking {
        val sourceAudio = File(appContext.cacheDir, "import-audio.m4a").apply {
            writeBytes(byteArrayOf(8, 7, 6, 5))
        }
        val progressEvents = mutableListOf<AudioTranscriptionProgress>()
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = true),
            audioTranscriptionEngine = FakeAudioTranscriptionEngine(
                result = AudioTranscriptionResult.Success(
                    transcript = "Final on-device transcript from audio chunks.",
                    durationMs = 1_200_000L,
                    chunkCount = 4
                ),
                progressEvents = listOf(
                    AudioTranscriptionProgress(0.25f, 300_000L, 1_200_000L, 1, 4),
                    AudioTranscriptionProgress(1f, 1_200_000L, 1_200_000L, 4, 4)
                )
            )
        )
        val importResult = repository.importAudio(Uri.fromFile(sourceAudio)) as DocumentImportResult.Success

        val result = repository.transcribeAudioDocument(importResult.documentId) {
            progressEvents += it
        }

        assertTrue(result is AudioTranscriptionResult.Success)
        assertEquals(2, progressEvents.size)
        assertEquals("Final on-device transcript from audio chunks.", repository.readFullText(importResult.documentId))
        val updated = database.documentDao().getById(importResult.documentId)
        assertFalse(updated?.isOnboarding ?: true)
        assertEquals("transcribed", updated?.onboardingStatus)
        assertTrue((updated?.extractedTextPreview ?: "").contains("Final on-device transcript"))
    }

    @Test
    fun transcribeAudioDocumentSkipsEngineWhenLiveTranscriptAlreadyPresent() = runBlocking {
        val sourceAudio = File(appContext.cacheDir, "live-short-circuit.m4a").apply {
            writeBytes(byteArrayOf(4, 4, 4))
        }
        var invocationCount = 0
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = true),
            audioTranscriptionEngine = FakeAudioTranscriptionEngine(
                result = AudioTranscriptionResult.Success("unused", 10L, 1)
            ) {
                invocationCount += 1
            }
        )
        val importResult = repository.importLiveRecording(
            sourceAudioFile = sourceAudio,
            transcript = "Live transcript captured from microphone."
        ) as DocumentImportResult.Success

        val result = repository.transcribeAudioDocument(importResult.documentId)

        val success = result as AudioTranscriptionResult.Success
        assertEquals("Live transcript captured from microphone.", success.transcript)
        assertEquals(0, invocationCount)
    }

    @Test
    fun transcribeAudioDocumentMarksFailureStateWhenEngineFails() = runBlocking {
        val sourceAudio = File(appContext.cacheDir, "import-audio-failure.m4a").apply {
            writeBytes(byteArrayOf(1, 3, 5, 7))
        }
        val repository = DocumentImportRepository(
            context = appContext,
            database = database,
            premiumAccessChecker = FakePremiumAccessChecker(isPremium = true),
            audioTranscriptionEngine = FakeAudioTranscriptionEngine(
                result = AudioTranscriptionResult.Failure("Recognizer unavailable.")
            )
        )
        val importResult = repository.importAudio(Uri.fromFile(sourceAudio)) as DocumentImportResult.Success

        val result = repository.transcribeAudioDocument(importResult.documentId)

        assertTrue(result is AudioTranscriptionResult.Failure)
        val updated = database.documentDao().getById(importResult.documentId)
        assertTrue(updated?.isOnboarding ?: false)
        assertEquals("transcription_failed", updated?.onboardingStatus)
    }

    private class FakePremiumAccessChecker(
        private val isPremium: Boolean
    ) : PremiumAccessChecker {
        override fun isPremiumUnlocked(): Boolean = isPremium
    }

    private class FakeAudioTranscriptionEngine(
        private val result: AudioTranscriptionResult,
        private val progressEvents: List<AudioTranscriptionProgress> = emptyList(),
        private val onTranscribe: () -> Unit = {}
    ) : AudioTranscriptionEngine {
        override suspend fun transcribe(
            audioFile: File,
            onProgress: (AudioTranscriptionProgress) -> Unit
        ): AudioTranscriptionResult {
            onTranscribe()
            progressEvents.forEach(onProgress)
            return result
        }
    }

    private fun buildPdf(file: File, lines: List<String>) {
        PDDocument().use { document ->
            lines.forEach { line ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(72f, 720f)
                    stream.showText(line)
                    stream.endText()
                }
            }
            document.save(file)
        }
    }
}
