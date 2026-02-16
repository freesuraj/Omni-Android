package com.suraj.apps.omni.core.data.analysis

import android.content.Context
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.importing.SharedPrefsPremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.OmniDatabaseFactory
import com.suraj.apps.omni.core.data.local.entity.PageAnalysisEntity
import com.suraj.apps.omni.core.data.provider.ProviderGatewayResult
import com.suraj.apps.omni.core.data.provider.StudyGenerationGateway
import com.suraj.apps.omni.core.model.DocumentFileType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DetailedAnalysisBootstrap(
    val documentTitle: String,
    val fileType: DocumentFileType,
    val isPremiumUnlocked: Boolean,
    val expectedAnalysisCount: Int,
    val analyses: List<PageAnalysisEntity>
)

data class DetailedAnalysisProgress(
    val completedCount: Int,
    val totalCount: Int,
    val label: String
)

sealed interface DetailedAnalysisGenerationResult {
    data class Success(
        val analyses: List<PageAnalysisEntity>,
        val expectedAnalysisCount: Int
    ) : DetailedAnalysisGenerationResult

    data object RequiresPremium : DetailedAnalysisGenerationResult
    data class Failure(val message: String) : DetailedAnalysisGenerationResult
}

class DetailedAnalysisRepository(
    context: Context,
    private val database: OmniDatabase = OmniDatabaseFactory.create(context),
    private val premiumAccessChecker: PremiumAccessChecker = SharedPrefsPremiumAccessChecker(context),
    private val generationGateway: StudyGenerationGateway = StudyGenerationGateway(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val importRepository = DocumentImportRepository(
        context = context,
        database = database,
        premiumAccessChecker = premiumAccessChecker,
        ioDispatcher = ioDispatcher
    )

    suspend fun loadBootstrap(documentId: String): DetailedAnalysisBootstrap? = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId) ?: return@withContext null
        val sourceText = importRepository.readFullText(documentId).orEmpty().trim()
        val expectedCount = buildAnalysisChunks(sourceText, document.fileType).size
        val analyses = database.pageAnalysisDao().getForDocument(documentId)

        DetailedAnalysisBootstrap(
            documentTitle = document.title,
            fileType = document.fileType,
            isPremiumUnlocked = importRepository.isPremiumUnlocked(),
            expectedAnalysisCount = expectedCount,
            analyses = analyses
        )
    }

    suspend fun generateAnalysis(
        documentId: String,
        retryFromScratch: Boolean,
        onProgress: (DetailedAnalysisProgress) -> Unit = {}
    ): DetailedAnalysisGenerationResult = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext DetailedAnalysisGenerationResult.Failure("Document not found.")

        if (!importRepository.isPremiumUnlocked()) {
            return@withContext DetailedAnalysisGenerationResult.RequiresPremium
        }

        val sourceText = importRepository.readFullText(documentId).orEmpty().trim()
        if (sourceText.isBlank()) {
            return@withContext DetailedAnalysisGenerationResult.Failure(
                "This document is still processing. Try analysis again in a moment."
            )
        }

        val chunks = buildAnalysisChunks(sourceText, document.fileType)
        if (chunks.isEmpty()) {
            return@withContext DetailedAnalysisGenerationResult.Failure(
                "No analyzable content found for this document."
            )
        }

        if (retryFromScratch) {
            database.pageAnalysisDao().deleteForDocument(document.id)
        }

        val existingByPage = database.pageAnalysisDao()
            .getForDocument(document.id)
            .associateBy { it.pageNumber }
            .toMutableMap()

        var completedCount = existingByPage.size.coerceAtMost(chunks.size)
        onProgress(
            DetailedAnalysisProgress(
                completedCount = completedCount,
                totalCount = chunks.size,
                label = progressLabel(document.fileType, completedCount.coerceAtLeast(1), chunks.size)
            )
        )

        for (chunk in chunks) {
            if (!retryFromScratch && existingByPage.containsKey(chunk.pageNumber)) {
                continue
            }

            val generatedContent = when (
                val providerResult = generationGateway.generateAnalysis(chunk.text)
            ) {
                is ProviderGatewayResult.Failure -> {
                    return@withContext DetailedAnalysisGenerationResult.Failure(providerResult.message)
                }

                is ProviderGatewayResult.Success -> providerResult.execution.value.trim()
            }

            val analysis = PageAnalysisEntity(
                id = "${document.id}-analysis-${chunk.pageNumber}",
                documentId = document.id,
                pageNumber = chunk.pageNumber,
                content = if (generatedContent.isBlank()) {
                    "No analysis could be generated for this section."
                } else {
                    generatedContent
                },
                thumbnailData = null,
                createdAtEpochMs = System.currentTimeMillis()
            )

            database.pageAnalysisDao().upsert(analysis)
            existingByPage[chunk.pageNumber] = analysis
            completedCount += 1

            onProgress(
                DetailedAnalysisProgress(
                    completedCount = completedCount,
                    totalCount = chunks.size,
                    label = progressLabel(document.fileType, completedCount, chunks.size)
                )
            )
        }

        val analyses = database.pageAnalysisDao().getForDocument(document.id)
        DetailedAnalysisGenerationResult.Success(
            analyses = analyses,
            expectedAnalysisCount = chunks.size
        )
    }
}

private data class AnalysisChunk(
    val pageNumber: Int,
    val text: String
)

private fun buildAnalysisChunks(
    fullText: String,
    fileType: DocumentFileType
): List<AnalysisChunk> {
    val normalizedText = fullText
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalizedText.isBlank()) return emptyList()

    val chunks = when (fileType) {
        DocumentFileType.PDF -> {
            val byFormFeed = fullText
                .split('\u000C')
                .map { it.trim() }
                .filter { it.wordCount() >= MIN_CHUNK_WORDS }
            when {
                byFormFeed.size >= 2 -> byFormFeed
                else -> chunkByWords(normalizedText, PDF_WORDS_PER_CHUNK)
            }
        }

        DocumentFileType.TXT,
        DocumentFileType.WEB,
        DocumentFileType.AUDIO -> {
            chunkByWords(normalizedText, CONTENT_WORDS_PER_CHUNK)
        }
    }.take(MAX_ANALYSIS_CHUNKS)

    return chunks.mapIndexed { index, text ->
        AnalysisChunk(pageNumber = index + 1, text = text)
    }
}

private fun progressLabel(fileType: DocumentFileType, completed: Int, total: Int): String {
    if (total <= 0) return "Preparing analysis"
    val safeCompleted = completed.coerceIn(0, total)
    return when (fileType) {
        DocumentFileType.PDF -> "Analyzing page $safeCompleted of $total"
        DocumentFileType.TXT,
        DocumentFileType.WEB,
        DocumentFileType.AUDIO -> "Analyzing section $safeCompleted of $total"
    }
}

private fun chunkByWords(text: String, wordsPerChunk: Int): List<String> {
    val words = text
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()

    return words
        .chunked(wordsPerChunk)
        .map { chunk -> chunk.joinToString(" ").trim() }
        .filter { it.wordCount() >= MIN_CHUNK_WORDS }
        .ifEmpty { listOf(text) }
}

private fun String.wordCount(): Int {
    return trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
}

private const val PDF_WORDS_PER_CHUNK = 220
private const val CONTENT_WORDS_PER_CHUNK = 180
private const val MIN_CHUNK_WORDS = 18
private const val MAX_ANALYSIS_CHUNKS = 24
