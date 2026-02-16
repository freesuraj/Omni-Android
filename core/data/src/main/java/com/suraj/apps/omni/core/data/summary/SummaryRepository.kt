package com.suraj.apps.omni.core.data.summary

import android.content.Context
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.importing.SharedPrefsPremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.OmniDatabaseFactory
import com.suraj.apps.omni.core.data.local.entity.DocumentSummaryEntity
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MIN_SUMMARY_WORDS = 80
const val FREE_MAX_SUMMARY_WORDS = 150
const val PREMIUM_MAX_SUMMARY_WORDS = 450
const val DEFAULT_SUMMARY_WORD_TARGET = 150

data class SummaryBootstrap(
    val documentTitle: String,
    val isPremiumUnlocked: Boolean,
    val summaries: List<DocumentSummaryEntity>
)

sealed interface SummaryGenerationResult {
    data class Success(val summary: DocumentSummaryEntity) : SummaryGenerationResult
    data object RequiresPremium : SummaryGenerationResult
    data class Failure(val message: String) : SummaryGenerationResult
}

class SummaryRepository(
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

    suspend fun loadBootstrap(documentId: String): SummaryBootstrap? = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId) ?: return@withContext null
        val summaries = database.documentSummaryDao().getForDocument(documentId)
            .sortedByDescending { it.createdAtEpochMs }

        SummaryBootstrap(
            documentTitle = document.title,
            isPremiumUnlocked = importRepository.isPremiumUnlocked(),
            summaries = summaries
        )
    }

    suspend fun generateSummary(
        documentId: String,
        targetWordCount: Int
    ): SummaryGenerationResult = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext SummaryGenerationResult.Failure("Document not found.")

        val isPremium = importRepository.isPremiumUnlocked()
        if (!isPremium && targetWordCount > FREE_MAX_SUMMARY_WORDS) {
            return@withContext SummaryGenerationResult.RequiresPremium
        }

        val maxWords = if (isPremium) PREMIUM_MAX_SUMMARY_WORDS else FREE_MAX_SUMMARY_WORDS
        val safeTarget = targetWordCount.coerceIn(MIN_SUMMARY_WORDS, maxWords)

        val fullText = importRepository.readFullText(documentId).orEmpty().trim()
        if (fullText.isBlank()) {
            return@withContext SummaryGenerationResult.Failure(
                "This document is still processing. Try generating summary in a moment."
            )
        }

        val summaryContent = buildSummary(fullText = fullText, targetWordCount = safeTarget)
        if (summaryContent.isBlank()) {
            return@withContext SummaryGenerationResult.Failure(
                "Unable to generate summary from this source."
            )
        }

        val summary = DocumentSummaryEntity(
            id = UUID.randomUUID().toString(),
            documentId = document.id,
            content = summaryContent,
            wordCount = summaryContent.wordCount(),
            createdAtEpochMs = System.currentTimeMillis()
        )

        database.documentSummaryDao().upsert(summary)
        SummaryGenerationResult.Success(summary)
    }
}

private fun buildSummary(
    fullText: String,
    targetWordCount: Int
): String {
    val sentences = fullText
        .replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.length >= 24 }

    if (sentences.isEmpty()) return ""

    val selected = mutableListOf<String>()
    var currentWords = 0
    for (sentence in sentences) {
        selected += sentence
        currentWords += sentence.wordCount()
        if (currentWords >= targetWordCount) break
    }

    if (selected.isEmpty()) return ""
    val merged = selected.joinToString(" ")
    val words = merged.split(Regex("\\s+"))
    if (words.size <= targetWordCount) return merged

    return words.take(targetWordCount).joinToString(" ").trimEnd('.', '!', '?') + "."
}

private fun String.wordCount(): Int {
    return trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
}
