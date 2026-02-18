package com.suraj.apps.omni.core.data.transcription

import java.io.File

data class AudioTranscriptionProgress(
    val progress: Float,
    val processedDurationMs: Long,
    val totalDurationMs: Long,
    val chunkIndex: Int,
    val chunkCount: Int
)

sealed interface AudioTranscriptionResult {
    data class Success(
        val transcript: String,
        val durationMs: Long,
        val chunkCount: Int
    ) : AudioTranscriptionResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : AudioTranscriptionResult
}

interface AudioTranscriptionEngine {
    suspend fun transcribe(
        audioFile: File,
        onProgress: (AudioTranscriptionProgress) -> Unit = {}
    ): AudioTranscriptionResult
}

class LocalAudioTranscriptionEngine : AudioTranscriptionEngine {
    override suspend fun transcribe(
        audioFile: File,
        onProgress: (AudioTranscriptionProgress) -> Unit
    ): AudioTranscriptionResult {
        // Standard Android SpeechRecognizer does not support transcribing from a file.
        // It requires live audio input via MIC or custom AudioRecord hook which is complex
        // and unreliable across devices without OS-level support (like Pixel's On-Device AI).
        //
        // For file-based transcription, we rely on cloud providers (Gemini) or
        // dedicated ML libraries (Vosk/Whisper - not included to keep app size small).
        return AudioTranscriptionResult.Failure("Local file transcription is not supported on this device.")
    }
}
