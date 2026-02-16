package com.suraj.apps.omni.core.data.qa

import android.content.Context
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.PremiumAccessChecker
import com.suraj.apps.omni.core.data.importing.SharedPrefsPremiumAccessChecker
import com.suraj.apps.omni.core.data.local.OmniDatabase
import com.suraj.apps.omni.core.data.local.OmniDatabaseFactory
import com.suraj.apps.omni.core.data.local.entity.QaMessageEntity
import com.suraj.apps.omni.core.data.provider.ProviderGatewayResult
import com.suraj.apps.omni.core.data.provider.StudyGenerationGateway
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QaBootstrap(
    val documentTitle: String,
    val isPremiumUnlocked: Boolean,
    val messages: List<QaMessageEntity>
)

sealed interface QaAskResult {
    data class Success(
        val userMessage: QaMessageEntity,
        val assistantMessage: QaMessageEntity
    ) : QaAskResult

    data object RequiresPremium : QaAskResult
    data class Failure(val message: String) : QaAskResult
}

class QaRepository(
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

    suspend fun loadBootstrap(documentId: String): QaBootstrap? = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId) ?: return@withContext null
        val messages = database.qaMessageDao().getForDocument(documentId)
        QaBootstrap(
            documentTitle = document.title,
            isPremiumUnlocked = importRepository.isPremiumUnlocked(),
            messages = messages
        )
    }

    suspend fun askQuestion(
        documentId: String,
        question: String
    ): QaAskResult = withContext(ioDispatcher) {
        val document = database.documentDao().getById(documentId)
            ?: return@withContext QaAskResult.Failure("Document not found.")

        val normalizedQuestion = normalizeQuestion(question)
        if (normalizedQuestion.isBlank()) {
            return@withContext QaAskResult.Failure("Enter a question first.")
        }

        if (!importRepository.isPremiumUnlocked()) {
            return@withContext QaAskResult.RequiresPremium
        }

        val now = System.currentTimeMillis()
        val userMessage = QaMessageEntity(
            id = UUID.randomUUID().toString(),
            documentId = document.id,
            content = normalizedQuestion,
            isUser = true,
            isError = false,
            createdAtEpochMs = now
        )
        database.qaMessageDao().upsert(userMessage)

        val fullText = importRepository.readFullText(document.id).orEmpty().trim()
        val assistantMessage = if (fullText.isBlank()) {
            createAssistantMessage(
                documentId = document.id,
                content = "This document is still processing. Try Q&A again in a moment.",
                isError = true,
                createdAtEpochMs = now + 1
            )
        } else {
            val generated = when (
                val providerResult = generationGateway.answerQuestion(
                    question = normalizedQuestion,
                    sourceText = fullText
                )
            ) {
                is ProviderGatewayResult.Failure -> {
                    "I ran into an error while generating an answer. ${providerResult.message}"
                }

                is ProviderGatewayResult.Success -> providerResult.execution.value
            }

            createAssistantMessage(
                documentId = document.id,
                content = generated,
                isError = generated.startsWith("I ran into an error", ignoreCase = true),
                createdAtEpochMs = now + 1
            )
        }

        database.qaMessageDao().upsert(assistantMessage)
        QaAskResult.Success(userMessage = userMessage, assistantMessage = assistantMessage)
    }

    private fun createAssistantMessage(
        documentId: String,
        content: String,
        isError: Boolean,
        createdAtEpochMs: Long
    ): QaMessageEntity {
        return QaMessageEntity(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            content = content,
            isUser = false,
            isError = isError,
            createdAtEpochMs = createdAtEpochMs
        )
    }
}

private fun normalizeQuestion(input: String): String {
    return input
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_QUESTION_LENGTH)
}

private const val MAX_QUESTION_LENGTH = 280
