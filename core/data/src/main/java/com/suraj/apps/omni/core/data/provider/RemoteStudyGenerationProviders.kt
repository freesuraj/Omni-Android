package com.suraj.apps.omni.core.data.provider

import com.suraj.apps.omni.core.model.QuizDifficulty
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val OMNI_GEMINI_MODEL = "gemini-2.0-flash-lite"
private const val GEMINI_DEFAULT_MODEL = "gemini-flash-latest"
private const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"
private const val CLAUDE_DEFAULT_MODEL = "claude-3-haiku-20240307"
private const val DEEPSEEK_DEFAULT_MODEL = "deepseek-chat"
private const val QWEN_DEFAULT_MODEL = "qwen-plus"

internal fun defaultRemoteProvider(
    providerId: AiProviderId,
    apiKey: String
): StudyGenerationProvider {
    val completionClient = when (providerId) {
        AiProviderId.OMNI ->
            GeminiCompletionClient(apiKey = apiKey, model = OMNI_GEMINI_MODEL)
        AiProviderId.OPENAI ->
            OpenAiCompatibleCompletionClient(apiKey = apiKey)
        AiProviderId.GEMINI ->
            GeminiCompletionClient(apiKey = apiKey, model = GEMINI_DEFAULT_MODEL)
        AiProviderId.ANTHROPIC ->
            ClaudeCompletionClient(apiKey = apiKey)
        AiProviderId.DEEPSEEK ->
            OpenAiCompatibleCompletionClient(
                apiKey = apiKey,
                baseUrl = "https://api.deepseek.com/v1",
                model = DEEPSEEK_DEFAULT_MODEL
            )
        AiProviderId.QWEN ->
            OpenAiCompatibleCompletionClient(
                apiKey = apiKey,
                baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                model = QWEN_DEFAULT_MODEL
            )
    }

    return PromptDrivenRemoteStudyGenerationProvider(completionClient)
}

private class PromptDrivenRemoteStudyGenerationProvider(
    private val completionClient: CompletionClient
) : StudyGenerationProvider {
    override suspend fun generateQuizQuestions(
        sourceText: String,
        questionCount: Int,
        difficulty: QuizDifficulty,
        includeSnippet: Boolean
    ): List<ProviderQuizQuestion> {
        if (questionCount <= 0) return emptyList()

        val systemPrompt = ProviderPrompts.quizSystemPrompt
        val userPrompt = ProviderPrompts.quizUserPrompt(
            count = questionCount,
            difficulty = difficulty.displayName(),
            text = sourceText.limitCharacters(15_000)
        )
        val response = completionClient.generateJson(systemPrompt = systemPrompt, userPrompt = userPrompt)
        return ProviderResponseParser.parseQuizQuestions(
            rawJson = response,
            includeSnippet = includeSnippet
        ).take(questionCount)
    }

    override suspend fun generateSummary(
        sourceText: String,
        targetWordCount: Int
    ): String {
        val systemPrompt = ProviderPrompts.summarySystemPrompt(targetWordCount)
        val userPrompt = ProviderPrompts.summaryUserPrompt(
            wordCount = targetWordCount,
            text = sourceText.limitCharacters(20_000)
        )
        return completionClient.generateText(systemPrompt = systemPrompt, userPrompt = userPrompt).trim()
    }

    override suspend fun generateNotes(
        sourceText: String,
        targetCount: Int
    ): List<ProviderStudyNote> {
        if (targetCount <= 0) return emptyList()

        val systemPrompt = ProviderPrompts.notesSystemPrompt
        val userPrompt = ProviderPrompts.notesUserPrompt(
            text = sourceText.limitCharacters(15_000),
            targetCount = targetCount
        )
        val response = completionClient.generateJson(systemPrompt = systemPrompt, userPrompt = userPrompt)
        return ProviderResponseParser.parseNotes(response).take(targetCount)
    }

    override suspend fun answerQuestion(
        question: String,
        sourceText: String
    ): String {
        val systemPrompt = ProviderPrompts.qaSystemPrompt
        val userPrompt = ProviderPrompts.qaUserPrompt(
            documentText = sourceText.limitCharacters(40_000),
            question = question
        )
        return completionClient.generateText(systemPrompt = systemPrompt, userPrompt = userPrompt).trim()
    }

    override suspend fun generateAnalysis(sourceText: String): String {
        val systemPrompt = ProviderPrompts.analysisSystemPrompt
        val userPrompt = ProviderPrompts.analysisUserPrompt(sourceText.limitCharacters(20_000))
        return completionClient.generateText(systemPrompt = systemPrompt, userPrompt = userPrompt).trim()
    }
}

private object ProviderPrompts {
    val quizSystemPrompt: String =
        """
        You are an exam question generator. Return ONLY valid JSON.
        The JSON must match this structure:
        {
          "questions": [
            {
              "prompt": "string",
              "optionA": "string",
              "optionB": "string",
              "correctAnswer": "A",
              "sourceSnippet": "string"
            }
          ]
        }
        """.trimIndent()

    fun quizUserPrompt(count: Int, difficulty: String, text: String): String =
        """
        Create $count questions (Difficulty: $difficulty) from the text below.
        Rules:
        1. Two options only (A and B).
        2. Exactly one correct answer.
        3. Include a short source snippet from the text supporting the correct answer.

        Text:
        $text
        """.trimIndent()

    val notesSystemPrompt: String =
        """
        You are a study flashcard generator. Return ONLY valid JSON.
        The goal is to create active recall questions.
        Structure:
        {
          "notes": [
            {
              "question": "string",
              "answer": "string"
            }
          ]
        }
        Rules:
        1. Question should be specific and clear.
        2. Answer should be concise (under 40 words).
        3. Return plain text only in answers (no Markdown).
        """.trimIndent()

    fun notesUserPrompt(text: String, targetCount: Int): String =
        """
        Create $targetCount study flashcards (Question & Answer) from the text below:

        $text
        """.trimIndent()

    fun summarySystemPrompt(wordCount: Int): String =
        """
        You are a professional summarizer.
        Create a concise summary of approximately $wordCount words.
        Rules:
        1. Capture the main ideas and key points.
        2. Write in clear, flowing paragraphs.
        3. Target word count: $wordCount words (±10%).
        4. NO intro like "Here's a summary", just the summary itself.
        """.trimIndent()

    fun summaryUserPrompt(wordCount: Int, text: String): String =
        "Summarize this text in approximately $wordCount words:\n\n$text"

    val qaSystemPrompt: String =
        """
        You are a highly intelligent and helpful academic tutor.
        Your goal is to answer questions based explicitly on the provided document.

        Guidelines:
        1. Use the document content as your primary source of truth.
        2. If the answer is in the document, explain it clearly and comprehensively.
        3. If the question asks for something not in the document, but related to the topic, you can provide brief context but clearly state it's not in the text.
        4. If the question is completely unrelated to the document, politely inform the user.
        5. Maintain a professional, encouraging, and educational tone.
        6. CRITICAL: Do NOT start your response with phrases like "According to the document" or "Based on the text". Start directly with the answer.
        """.trimIndent()

    fun qaUserPrompt(documentText: String, question: String): String =
        "Document content:\n$documentText\n\nUser Question: $question"

    val analysisSystemPrompt: String =
        """
        You are an expert academic analyst. Provide clear, insight-driven analysis in Markdown.
        Use section headers, bullets for key observations, and concise language.
        """.trimIndent()

    fun analysisUserPrompt(text: String): String =
        """
        Analyze this document excerpt and provide detailed insights using Markdown.

        Requirements:
        - Use **bold** for key terms.
        - Use bullet points for lists.
        - Include section headers (##) such as Main Themes, Key Insights, and Summary.
        - Focus on understanding and practical takeaways, not only restating text.

        --- DOCUMENT CONTENT ---
        $text
        """.trimIndent()
}

private object ProviderResponseParser {
    fun parseQuizQuestions(rawJson: String, includeSnippet: Boolean): List<ProviderQuizQuestion> {
        val root = JSONObject(cleanJsonMarkdown(rawJson))
        val questionsArray = root.optJSONArray("questions") ?: JSONArray()
        val result = mutableListOf<ProviderQuizQuestion>()

        for (index in 0 until questionsArray.length()) {
            val item = questionsArray.optJSONObject(index) ?: continue
            val prompt = item.optString("prompt").trim()
            val optionA = item.optString("optionA").trim()
            val optionB = item.optString("optionB").trim()
            if (prompt.isBlank() || optionA.isBlank() || optionB.isBlank()) continue

            val correct = item.optString("correctAnswer").trim().uppercase()
            val normalizedCorrect = if (correct == "B") "B" else "A"
            val snippet = item.optString("sourceSnippet").trim().ifBlank { null }

            result += ProviderQuizQuestion(
                prompt = prompt,
                optionA = optionA,
                optionB = optionB,
                correctAnswer = normalizedCorrect,
                sourceSnippet = if (includeSnippet) snippet else null
            )
        }
        return result
    }

    fun parseNotes(rawJson: String): List<ProviderStudyNote> {
        val root = JSONObject(cleanJsonMarkdown(rawJson))
        val notesArray = root.optJSONArray("notes") ?: JSONArray()
        val result = mutableListOf<ProviderStudyNote>()

        for (index in 0 until notesArray.length()) {
            val item = notesArray.optJSONObject(index) ?: continue
            val question = item.optString("question").trim()
            val answer = stripMarkdownFormatting(item.optString("answer").trim())
            if (question.isBlank() || answer.isBlank()) continue
            result += ProviderStudyNote(front = question, back = answer)
        }
        return result
    }
}

private interface CompletionClient {
    suspend fun generateJson(systemPrompt: String, userPrompt: String): String
    suspend fun generateText(systemPrompt: String, userPrompt: String): String
}

private class OpenAiCompatibleCompletionClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = OPENAI_DEFAULT_MODEL
) : CompletionClient {
    private val endpoint: String = "${baseUrl.trimEnd('/')}/chat/completions"

    override suspend fun generateJson(systemPrompt: String, userPrompt: String): String {
        val payload = basePayload(systemPrompt, userPrompt).apply {
            put("response_format", JSONObject().put("type", "json_object"))
        }
        val response = executeJsonPost(
            url = endpoint,
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            requestBody = payload
        )
        return extractOpenAiContent(response)
    }

    override suspend fun generateText(systemPrompt: String, userPrompt: String): String {
        val response = executeJsonPost(
            url = endpoint,
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            requestBody = basePayload(systemPrompt, userPrompt)
        )
        return extractOpenAiContent(response)
    }

    private fun basePayload(systemPrompt: String, userPrompt: String): JSONObject {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        return JSONObject()
            .put("model", model)
            .put("messages", messages)
    }
}

private class GeminiCompletionClient(
    private val apiKey: String,
    private val model: String
) : CompletionClient {
    private val endpoint: String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    override suspend fun generateJson(systemPrompt: String, userPrompt: String): String {
        val response = executeJsonPost(
            url = endpoint,
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = basePayload(systemPrompt, userPrompt).put(
                "generationConfig",
                JSONObject().put("response_mime_type", "application/json")
            )
        )
        return extractGeminiContent(response)
    }

    override suspend fun generateText(systemPrompt: String, userPrompt: String): String {
        val response = executeJsonPost(
            url = endpoint,
            headers = mapOf("Content-Type" to "application/json"),
            requestBody = basePayload(systemPrompt, userPrompt)
        )
        return extractGeminiContent(response)
    }

    private fun basePayload(systemPrompt: String, userPrompt: String): JSONObject {
        val merged = "$systemPrompt\n\n$userPrompt"
        val parts = JSONArray().put(JSONObject().put("text", merged))
        val content = JSONArray().put(JSONObject().put("parts", parts))
        return JSONObject().put("contents", content)
    }
}

private class ClaudeCompletionClient(
    private val apiKey: String,
    private val model: String = CLAUDE_DEFAULT_MODEL
) : CompletionClient {
    private val endpoint = "https://api.anthropic.com/v1/messages"

    override suspend fun generateJson(systemPrompt: String, userPrompt: String): String {
        return generate(systemPrompt = systemPrompt, userPrompt = userPrompt, maxTokens = 4_096)
    }

    override suspend fun generateText(systemPrompt: String, userPrompt: String): String {
        return generate(systemPrompt = systemPrompt, userPrompt = userPrompt, maxTokens = 2_048)
    }

    private suspend fun generate(systemPrompt: String, userPrompt: String, maxTokens: Int): String {
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", userPrompt)
        )
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", systemPrompt)
            .put("messages", messages)

        val response = executeJsonPost(
            url = endpoint,
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            ),
            requestBody = payload
        )
        return extractClaudeContent(response)
    }
}

private suspend fun executeJsonPost(
    url: String,
    headers: Map<String, String>,
    requestBody: JSONObject
): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection)
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 45_000
        connection.doInput = true
        connection.doOutput = true
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

        connection.outputStream.use { stream ->
            stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (statusCode !in 200..299) {
            throw IOException("HTTP $statusCode: ${responseText.ifBlank { "Unknown provider error." }}")
        }
        responseText
    } finally {
        connection.disconnect()
    }
}

private fun extractOpenAiContent(responseBody: String): String {
    val root = JSONObject(responseBody)
    val content = root.optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
        ?.trim()
    if (content.isNullOrBlank()) throw IOException("Provider returned no message content.")
    return content
}

private fun extractGeminiContent(responseBody: String): String {
    val root = JSONObject(responseBody)
    val content = root.optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?.optJSONObject(0)
        ?.optString("text")
        ?.trim()
    if (content.isNullOrBlank()) throw IOException("Provider returned no candidate content.")
    return content
}

private fun extractClaudeContent(responseBody: String): String {
    val root = JSONObject(responseBody)
    val content = root.optJSONArray("content")
        ?.optJSONObject(0)
        ?.optString("text")
        ?.trim()
    if (content.isNullOrBlank()) throw IOException("Provider returned no text content.")
    return content
}

private fun QuizDifficulty.displayName(): String {
    return when (this) {
        QuizDifficulty.EASY -> "Easy"
        QuizDifficulty.MEDIUM -> "Medium"
        QuizDifficulty.HARD -> "Hard"
    }
}

private fun String.limitCharacters(maxCharacters: Int): String {
    val trimmed = trim()
    if (trimmed.length <= maxCharacters) return trimmed
    return trimmed.take(maxCharacters)
}

private fun cleanJsonMarkdown(rawText: String): String {
    var cleaned = rawText.trim()
    if (cleaned.startsWith("```json")) cleaned = cleaned.removePrefix("```json")
    if (cleaned.startsWith("```")) cleaned = cleaned.removePrefix("```")
    if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```")
    return cleaned.trim()
}

private fun stripMarkdownFormatting(text: String): String {
    return text
        .replace(Regex("`{1,3}"), "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("_(.*?)_"), "$1")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
        .trim()
}
