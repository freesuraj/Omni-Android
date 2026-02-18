package com.suraj.apps.omni.core.data.importing

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.webkit.URLUtil
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.suraj.apps.omni.core.data.entitlement.PremiumAccessStore
import com.suraj.apps.omni.core.data.entitlement.SharedPrefsPremiumAccessStore
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.OmniDatabaseFactory
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionEngine
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionProgress
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionResult
import com.suraj.apps.omni.core.data.transcription.OnDeviceAudioTranscriptionEngine
import com.suraj.apps.omni.core.model.DocumentFileType
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface DocumentImportResult {
    data class Success(val documentId: String) : DocumentImportResult
    data object RequiresPremium : DocumentImportResult
    data class Failure(val message: String) : DocumentImportResult
}

interface PremiumAccessChecker {
    fun isPremiumUnlocked(): Boolean
}

class SharedPrefsPremiumAccessChecker(
    private val premiumAccessStore: PremiumAccessStore
) : PremiumAccessChecker {
    constructor(context: Context) : this(SharedPrefsPremiumAccessStore(context))

    override fun isPremiumUnlocked(): Boolean {
        return premiumAccessStore.isPremiumUnlocked()
    }
}

class DocumentImportRepository(
    private val context: Context,
    private val database: OmniDatabase = OmniDatabaseFactory.create(context),
    private val premiumAccessChecker: PremiumAccessChecker = SharedPrefsPremiumAccessChecker(context),
    private val premiumAccessStore: PremiumAccessStore = SharedPrefsPremiumAccessStore(context),
    private val audioTranscriptionEngine: AudioTranscriptionEngine = OnDeviceAudioTranscriptionEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun isPremiumUnlocked(): Boolean = premiumAccessChecker.isPremiumUnlocked()

    fun observeDocuments(): Flow<List<DocumentEntity>> = database.documentDao().observeAll()
    fun observeDocument(documentId: String): Flow<DocumentEntity?> =
        database.documentDao().observeById(documentId)
    fun observeLatestQuizQuestionCount(documentId: String): Flow<Int> =
        database.quizDao().observeLatestQuizQuestionCount(documentId)
    fun observeStudyNoteCount(documentId: String): Flow<Int> =
        database.studyNoteDao().observeCountForDocument(documentId)

    suspend fun getDocument(documentId: String): DocumentEntity? = withContext(ioDispatcher) {
        database.documentDao().getById(documentId)
    }

    suspend fun updateOnboardingState(
        documentId: String,
        isOnboarding: Boolean,
        onboardingStatus: String?
    ) = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId) ?: return@withContext
        database.documentDao().upsert(
            document.copy(
                isOnboarding = isOnboarding,
                onboardingStatus = onboardingStatus
            )
        )
    }

    suspend fun importDocument(uri: Uri): DocumentImportResult = withContext(ioDispatcher) {
        if (!canImportDocument()) {
            return@withContext DocumentImportResult.RequiresPremium
        }

        val detectedType = detectDocumentType(uri)
            ?: return@withContext DocumentImportResult.Failure("Unsupported document type.")
        importUri(uri = uri, fileType = detectedType)
    }

    suspend fun importAudio(uri: Uri): DocumentImportResult = withContext(ioDispatcher) {
        if (!canImportDocument()) {
            return@withContext DocumentImportResult.RequiresPremium
        }
        importUri(uri = uri, fileType = DocumentFileType.AUDIO)
    }

    suspend fun importLiveRecording(
        sourceAudioFile: File,
        transcript: String
    ): DocumentImportResult = withContext(ioDispatcher) {
        if (!canCreateLiveRecording()) {
            return@withContext DocumentImportResult.RequiresPremium
        }
        if (!sourceAudioFile.exists()) {
            return@withContext DocumentImportResult.Failure("Recording file is missing.")
        }

        val documentId = UUID.randomUUID().toString()
        val extension = sourceAudioFile.extension.ifBlank { "m4a" }
        val storedAudio = copyLocalFile(
            source = sourceAudioFile,
            documentId = documentId,
            extension = extension
        ) ?: return@withContext DocumentImportResult.Failure("Unable to store recorded audio.")

        val normalizedTranscript = transcript
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Live recording captured. Transcript will appear after processing." }
        val preview = previewFromText(
            fullText = normalizedTranscript,
            fallback = "Live recording imported."
        )

        persistFullText(documentId, normalizedTranscript)
        database.documentDao().upsert(
            DocumentEntity(
                id = documentId,
                title = defaultLiveRecordingTitle(),
                createdAtEpochMs = System.currentTimeMillis(),
                fileBookmarkData = storedAudio.absolutePath.toByteArray(Charsets.UTF_8),
                fileType = DocumentFileType.AUDIO,
                sourceUrl = null,
                extractedTextHash = sha256(normalizedTranscript),
                extractedTextPreview = preview,
                lastOpenedAtEpochMs = null,
                isOnboarding = true,
                onboardingStatus = "imported",
                timeSpentSeconds = 0.0
            )
        )
        recordLiveRecordingCreation()
        DocumentImportResult.Success(documentId)
    }

    suspend fun importWebArticle(url: String): DocumentImportResult = withContext(ioDispatcher) {
        if (!canImportDocument()) {
            return@withContext DocumentImportResult.RequiresPremium
        }
        val normalizedUrl = normalizeUrl(url)
            ?: return@withContext DocumentImportResult.Failure("Please enter a valid URL.")

        val articleText = fetchWebText(normalizedUrl)
            ?: return@withContext DocumentImportResult.Failure("Unable to fetch web article.")

        val documentId = UUID.randomUUID().toString()
        val title = runCatching { Uri.parse(normalizedUrl).host.orEmpty() }
            .getOrDefault("Web article")
            .ifBlank { "Web article" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val preview = previewFromText(
            fullText = articleText,
            fallback = "Web article imported."
        )

        persistFullText(documentId, articleText)
        database.documentDao().upsert(
            DocumentEntity(
                id = documentId,
                title = title,
                createdAtEpochMs = System.currentTimeMillis(),
                fileBookmarkData = null,
                fileType = DocumentFileType.WEB,
                sourceUrl = normalizedUrl,
                extractedTextHash = sha256(articleText),
                extractedTextPreview = preview,
                lastOpenedAtEpochMs = null,
                isOnboarding = true,
                onboardingStatus = "imported",
                timeSpentSeconds = 0.0
            )
        )
        DocumentImportResult.Success(documentId)
    }

    suspend fun transcribeAudioDocument(
        documentId: String,
        onProgress: (AudioTranscriptionProgress) -> Unit = {}
    ): AudioTranscriptionResult = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext AudioTranscriptionResult.Failure("Document not found.")
        if (document.fileType != DocumentFileType.AUDIO) {
            return@withContext AudioTranscriptionResult.Failure("Document is not an audio source.")
        }

        val existingTranscript = readFullText(documentId).orEmpty().trim()
        if (existingTranscript.isNotBlank() && !isPlaceholderAudioTranscript(existingTranscript)) {
            onProgress(
                AudioTranscriptionProgress(
                    progress = 1f,
                    processedDurationMs = 0L,
                    totalDurationMs = 0L,
                    chunkIndex = 1,
                    chunkCount = 1
                )
            )
            return@withContext AudioTranscriptionResult.Success(
                transcript = existingTranscript,
                durationMs = 0L,
                chunkCount = 1
            )
        }

        val audioPath = document.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
        if (audioPath.isBlank()) {
            return@withContext AudioTranscriptionResult.Failure("Audio file reference is missing.")
        }
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            return@withContext AudioTranscriptionResult.Failure("Audio file is missing.")
        }

        database.documentDao().upsert(
            document.copy(isOnboarding = true, onboardingStatus = "transcribing")
        )

        when (val result = audioTranscriptionEngine.transcribe(audioFile, onProgress)) {
            is AudioTranscriptionResult.Failure -> {
                val current = database.documentDao().getById(documentId) ?: document
                database.documentDao().upsert(
                    current.copy(isOnboarding = true, onboardingStatus = "transcription_failed")
                )
                result
            }

            is AudioTranscriptionResult.Success -> {
                val normalizedTranscript = result.transcript
                    .replace(Regex("\\s+"), " ")
                    .trim()
                persistFullText(documentId, normalizedTranscript)
                val current = database.documentDao().getById(documentId) ?: document
                val preview = previewFromText(
                    fullText = normalizedTranscript,
                    fallback = fallbackPreview(DocumentFileType.AUDIO)
                )
                database.documentDao().upsert(
                    current.copy(
                        extractedTextHash = sha256(normalizedTranscript),
                        extractedTextPreview = preview,
                        isOnboarding = false,
                        onboardingStatus = "transcribed"
                    )
                )
                result.copy(transcript = normalizedTranscript)
            }
        }
    }

    suspend fun readFullText(documentId: String): String? = withContext(ioDispatcher) {
        val textFile = textFileFor(documentId)
        if (!textFile.exists()) return@withContext null
        textFile.readText()
    }

    fun isPlaceholderAudioTranscript(value: String?): Boolean {
        val normalized = value.orEmpty().replace(Regex("\\s+"), " ").trim()
        return isPlaceholderAudioTranscriptValue(normalized)
    }

    suspend fun renameDocument(
        documentId: String,
        title: String
    ): DocumentImportResult = withContext(ioDispatcher) {
        val normalizedTitle = title
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalizedTitle.isBlank()) {
            return@withContext DocumentImportResult.Failure("Document title cannot be empty.")
        }

        val existing = database.documentDao().getById(documentId)
            ?: return@withContext DocumentImportResult.Failure("Document not found.")
        database.documentDao().rename(documentId = existing.id, title = normalizedTitle)
        DocumentImportResult.Success(existing.id)
    }

    suspend fun deleteDocument(documentId: String): DocumentImportResult = withContext(ioDispatcher) {
        val existing = database.documentDao().getById(documentId)
            ?: return@withContext DocumentImportResult.Failure("Document not found.")
        database.documentDao().deleteById(documentId)
        deleteDocumentArtifacts(existing)
        DocumentImportResult.Success(documentId)
    }

    fun remainingFreeLiveRecordings(): Int {
        if (premiumAccessChecker.isPremiumUnlocked()) return Int.MAX_VALUE
        val remaining = FREE_LIVE_RECORDING_LIMIT - liveRecordingCreationCount()
        return remaining.coerceAtLeast(0)
    }

    private suspend fun importUri(
        uri: Uri,
        fileType: DocumentFileType
    ): DocumentImportResult = withContext(ioDispatcher) {
        val documentId = UUID.randomUUID().toString()
        val displayName = resolveDisplayName(uri).orEmpty().ifBlank { defaultNameFor(fileType) }
        val extension = extensionFor(uri, displayName, fileType)
        val storedFile = copySourceFile(uri, documentId, extension)
            ?: return@withContext DocumentImportResult.Failure("Unable to import selected file.")
        val extractedText = extractText(uri, storedFile, fileType)
        val preview = previewFromText(
            fullText = extractedText,
            fallback = fallbackPreview(fileType)
        )

        persistFullText(documentId, extractedText)
        database.documentDao().upsert(
            DocumentEntity(
                id = documentId,
                title = displayName.removeSuffix(".$extension"),
                createdAtEpochMs = System.currentTimeMillis(),
                fileBookmarkData = storedFile.absolutePath.toByteArray(Charsets.UTF_8),
                fileType = fileType,
                sourceUrl = null,
                extractedTextHash = sha256(extractedText),
                extractedTextPreview = preview,
                lastOpenedAtEpochMs = null,
                isOnboarding = true,
                onboardingStatus = "imported",
                timeSpentSeconds = 0.0
            )
        )
        DocumentImportResult.Success(documentId)
    }

    private suspend fun canImportDocument(): Boolean {
        if (premiumAccessChecker.isPremiumUnlocked()) return true
        return database.documentDao().count() < FREE_DOCUMENT_LIMIT
    }

    private fun canCreateLiveRecording(): Boolean {
        if (premiumAccessChecker.isPremiumUnlocked()) return true
        return liveRecordingCreationCount() < FREE_LIVE_RECORDING_LIMIT
    }

    private fun recordLiveRecordingCreation() {
        if (premiumAccessChecker.isPremiumUnlocked()) return
        premiumAccessStore.setLiveRecordingsCreated(liveRecordingCreationCount() + 1)
    }

    private fun liveRecordingCreationCount(): Int {
        return premiumAccessStore.getLiveRecordingsCreated()
    }

    private fun detectDocumentType(uri: Uri): DocumentFileType? {
        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
        val extension = uri.lastPathSegment?.substringAfterLast('.', "").orEmpty().lowercase()
        return when {
            mimeType.contains("pdf") || extension == "pdf" -> DocumentFileType.PDF
            mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS -> DocumentFileType.TXT
            else -> null
        }
    }

    private fun extractText(uri: Uri, copiedFile: File, fileType: DocumentFileType): String {
        return when (fileType) {
            DocumentFileType.TXT -> runCatching {
                copiedFile.readText()
            }.getOrElse {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
            }.ifBlank { "Imported text file." }

            DocumentFileType.PDF -> extractPdfText(copiedFile)
                ?: "PDF imported. Unable to extract readable text."
            DocumentFileType.AUDIO -> "Audio imported. Transcription runs in onboarding."
            DocumentFileType.WEB -> "Web article imported."
        }
    }

    private fun extractPdfText(copiedFile: File): String? {
        initializePdfBoxIfNeeded()
        return runCatching {
            PDDocument.load(copiedFile).use { document ->
                if (document.numberOfPages <= 0) return@use ""
                PDFTextStripper()
                    .apply { sortByPosition = true }
                    .getText(document)
            }
        }.getOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.ifBlank { null }
    }

    private fun initializePdfBoxIfNeeded() {
        if (isPdfBoxInitialized) return
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            isPdfBoxInitialized = true
        }
    }

    private fun fallbackPreview(fileType: DocumentFileType): String = when (fileType) {
        DocumentFileType.PDF -> "PDF imported."
        DocumentFileType.AUDIO -> "Audio imported."
        DocumentFileType.TXT -> "Text imported."
        DocumentFileType.WEB -> "Web article imported."
    }

    private fun defaultNameFor(fileType: DocumentFileType): String = when (fileType) {
        DocumentFileType.PDF -> "Imported PDF"
        DocumentFileType.TXT -> "Imported Text"
        DocumentFileType.AUDIO -> "Imported Audio"
        DocumentFileType.WEB -> "Imported Article"
    }

    private fun previewFromText(fullText: String, fallback: String): String {
        return fullText.lineSequence()
            .flatMap { it.split(" ").asSequence() }
            .filter { it.isNotBlank() }
            .take(36)
            .joinToString(" ")
            .ifBlank { fallback }
    }

    private fun isPlaceholderAudioTranscriptValue(value: String): Boolean {
        return value.equals(PLACEHOLDER_IMPORTED_AUDIO_TEXT, ignoreCase = true) ||
            value.equals(PLACEHOLDER_LIVE_AUDIO_TEXT, ignoreCase = true) ||
            value.startsWith(PLACEHOLDER_HEURISTIC_AUDIO_PREFIX, ignoreCase = true)
    }

    private fun normalizeUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        val normalized = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        return if (URLUtil.isValidUrl(normalized)) normalized else null
    }

    private fun fetchWebText(url: String): String? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Omni-Android/1.0")
            }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace(Regex("\\s+"), " ")
                .trim()
        }.getOrNull()
    }

    private fun persistFullText(documentId: String, fullText: String) {
        val output = textFileFor(documentId)
        output.parentFile?.mkdirs()
        output.writeText(fullText)
    }

    private fun deleteDocumentArtifacts(document: DocumentEntity) {
        runCatching {
            val textFile = textFileFor(document.id)
            if (textFile.exists()) {
                textFile.delete()
            }
        }
        runCatching {
            val path = document.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
            if (path.isBlank()) return@runCatching
            val file = File(path)
            if (!file.exists()) return@runCatching
            if (file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                file.delete()
            }
        }
    }

    private fun textFileFor(documentId: String): File {
        return File(context.filesDir, "text/$documentId.txt")
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        ) ?: return uri.lastPathSegment
        return cursor.useDisplayName()
    }

    private fun extensionFor(uri: Uri, displayName: String, fileType: DocumentFileType): String {
        val byName = displayName.substringAfterLast('.', "").lowercase()
        if (byName.isNotBlank()) return byName
        val byPath = uri.lastPathSegment?.substringAfterLast('.', "").orEmpty().lowercase()
        if (byPath.isNotBlank()) return byPath
        return when (fileType) {
            DocumentFileType.PDF -> "pdf"
            DocumentFileType.TXT -> "txt"
            DocumentFileType.AUDIO -> "m4a"
            DocumentFileType.WEB -> "txt"
        }
    }

    private fun copySourceFile(uri: Uri, documentId: String, extension: String): File? {
        val destination = File(context.filesDir, "imports/$documentId.$extension")
        destination.parentFile?.mkdirs()
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destination
        }.getOrNull()
    }

    private fun copyLocalFile(source: File, documentId: String, extension: String): File? {
        val destination = File(context.filesDir, "imports/$documentId.$extension")
        destination.parentFile?.mkdirs()
        return runCatching {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destination
        }.getOrNull()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun Cursor.useDisplayName(): String? = use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex == -1) return@use null
        cursor.getString(columnIndex)
    }

    companion object {
        const val FREE_DOCUMENT_LIMIT = 1
        const val FREE_LIVE_RECORDING_LIMIT = 2
        private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "rtf")
        private const val PLACEHOLDER_IMPORTED_AUDIO_TEXT =
            "Audio imported. Transcription runs in onboarding."
        private const val PLACEHOLDER_LIVE_AUDIO_TEXT =
            "Live recording captured. Transcript will appear after processing."
        private const val PLACEHOLDER_HEURISTIC_AUDIO_PREFIX = "Audio segment "
        @Volatile private var isPdfBoxInitialized: Boolean = false
    }

    private fun defaultLiveRecordingTitle(): String {
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return "Live recording ${formatter.format(Date())}"
    }
}
