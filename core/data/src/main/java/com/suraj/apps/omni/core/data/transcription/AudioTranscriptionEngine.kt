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
