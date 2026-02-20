package com.suraj.apps.omni.core.data.transcription

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiAudioTranscriptionEngine(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash-lite"
) : AudioTranscriptionEngine {

    private val endpoint: String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    override suspend fun transcribe(
        audioFile: File,
        onProgress: (AudioTranscriptionProgress) -> Unit
    ): AudioTranscriptionResult = withContext(Dispatchers.IO) {
        if (!audioFile.exists()) {
            return@withContext AudioTranscriptionResult.Failure("Audio file is missing.")
        }
        if (apiKey.isBlank()) {
            return@withContext AudioTranscriptionResult.Failure("Gemini API Key is missing.")
        }

        // 1. Read file bytes and encode to Base64
        val fileBytes = runCatching { audioFile.readBytes() }.getOrElse {
            return@withContext AudioTranscriptionResult.Failure("Failed to read audio file.", it)
        }
        if (fileBytes.isEmpty()) {
            return@withContext AudioTranscriptionResult.Failure("Audio file is empty.")
        }

        // Report progress: starting upload/processing
        onProgress(
            AudioTranscriptionProgress(
                progress = 0.1f,
                processedDurationMs = 0L,
                totalDurationMs = 0L, // Unknown total duration until processing completes or we read metadata
                chunkIndex = 0,
                chunkCount = 1
            )
        )

        val base64Audio = Base64.getEncoder().encodeToString(fileBytes)
        val mimeType = when (audioFile.extension.lowercase()) {
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "m4a", "mp4", "aac" -> "audio/mp4" // Gemini handles m4a/aac as audio/mp4 container usually
            else -> "audio/mp4" // Default fallback
        }

        // 2. Construct JSON payload
        // {
        //   "contents": [{
        //     "parts": [
        //       { "text": "Transcribe this audio file verbatim." },
        //       { "inline_data": { "mime_type": "...", "data": "..." } }
        //     ]
        //   }]
        // }
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", "Transcribe this audio file verbatim. Output only the transcript text, no markdown, no timestamps, no speaker labels unless essential."))
                    put(JSONObject().put("inline_data", JSONObject().apply {
                        put("mime_type", mimeType)
                        put("data", base64Audio)
                    }))
                })
            }))
            // Add generation config to encourage plain text
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        // 3. Send request
        val responseJson = runCatching {
            executeJsonPost(endpoint, payload)
        }.getOrElse {
            return@withContext AudioTranscriptionResult.Failure("Gemini API request failed.", it)
        }

        // 4. Parse response
        val transcript = extractGeminiContent(responseJson)
        if (transcript.isBlank()) {
            return@withContext AudioTranscriptionResult.Failure("Gemini returned empty transcript.")
        }

        // Report completion
        onProgress(
            AudioTranscriptionProgress(
                progress = 1.0f,
                processedDurationMs = 0L,
                totalDurationMs = 0L,
                chunkIndex = 1,
                chunkCount = 1
            )
        )

        AudioTranscriptionResult.Success(
            transcript = transcript,
            durationMs = 0L, // Gemini doesn't return duration in this payload
            chunkCount = 1
        )
    }

    private fun executeJsonPost(url: String, requestBody: JSONObject): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 60_000 // Longer timeout for audio upload
            connection.readTimeout = 60_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            connection.outputStream.use { stream ->
                stream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IOException("HTTP $statusCode: ${responseText.ifBlank { "Unknown Gemini error." }}")
            }
            return responseText
        } finally {
            connection.disconnect()
        }
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
        return content.orEmpty()
    }
}