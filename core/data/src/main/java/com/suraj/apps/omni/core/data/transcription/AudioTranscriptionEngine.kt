package com.suraj.apps.omni.core.data.transcription

import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.min

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

data class AudioChunkWindow(
    val startMs: Long,
    val endMs: Long,
    val index: Int,
    val totalChunks: Int
)

fun interface AudioDurationResolver {
    fun resolveDurationMs(audioFile: File): Long?
}

fun interface AudioChunkTranscriber {
    suspend fun transcribeChunk(audioFile: File, chunk: AudioChunkWindow): String
}

class OnDeviceAudioTranscriptionEngine(
    private val durationResolver: AudioDurationResolver = MediaMetadataAudioDurationResolver(),
    private val chunkTranscriber: AudioChunkTranscriber = HeuristicOnDeviceChunkTranscriber(),
    private val shortAudioThresholdMs: Long = SHORT_AUDIO_THRESHOLD_MS,
    private val longAudioChunkSizeMs: Long = LONG_AUDIO_CHUNK_SIZE_MS
) : AudioTranscriptionEngine {

    override suspend fun transcribe(
        audioFile: File,
        onProgress: (AudioTranscriptionProgress) -> Unit
    ): AudioTranscriptionResult {
        if (!audioFile.exists()) {
            return AudioTranscriptionResult.Failure("Audio file is missing.")
        }
        val durationMs = durationResolver.resolveDurationMs(audioFile)
            ?: return AudioTranscriptionResult.Failure("Unable to read audio duration.")
        if (durationMs <= 0L) {
            return AudioTranscriptionResult.Failure("Audio duration is invalid.")
        }

        val chunks = buildChunks(durationMs, shortAudioThresholdMs, longAudioChunkSizeMs)
        if (chunks.isEmpty()) {
            return AudioTranscriptionResult.Failure("Unable to segment audio for transcription.")
        }

        onProgress(
            AudioTranscriptionProgress(
                progress = 0f,
                processedDurationMs = 0L,
                totalDurationMs = durationMs,
                chunkIndex = 0,
                chunkCount = chunks.size
            )
        )

        val transcriptSegments = mutableListOf<String>()
        for (chunk in chunks) {
            val segment = runCatching {
                chunkTranscriber.transcribeChunk(audioFile, chunk)
            }.getOrElse { error ->
                return AudioTranscriptionResult.Failure(
                    message = "Audio transcription failed on chunk ${chunk.index + 1}.",
                    cause = error
                )
            }

            val normalized = segment.replace(Regex("\\s+"), " ").trim()
            if (normalized.isNotEmpty()) {
                transcriptSegments += normalized
            }
            val processedDuration = chunk.endMs
            val progress = (processedDuration.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            onProgress(
                AudioTranscriptionProgress(
                    progress = progress,
                    processedDurationMs = processedDuration,
                    totalDurationMs = durationMs,
                    chunkIndex = chunk.index + 1,
                    chunkCount = chunk.totalChunks
                )
            )
        }

        val combinedTranscript = transcriptSegments.joinToString(separator = " ").trim()
        if (combinedTranscript.isEmpty()) {
            return AudioTranscriptionResult.Failure("No speech content was produced from audio chunks.")
        }

        return AudioTranscriptionResult.Success(
            transcript = combinedTranscript,
            durationMs = durationMs,
            chunkCount = chunks.size
        )
    }

    companion object {
        const val SHORT_AUDIO_THRESHOLD_MS = 10 * 60 * 1000L
        const val LONG_AUDIO_CHUNK_SIZE_MS = 5 * 60 * 1000L

        internal fun buildChunks(
            durationMs: Long,
            shortAudioThresholdMs: Long = SHORT_AUDIO_THRESHOLD_MS,
            longAudioChunkSizeMs: Long = LONG_AUDIO_CHUNK_SIZE_MS
        ): List<AudioChunkWindow> {
            if (durationMs <= 0L) return emptyList()
            if (durationMs <= shortAudioThresholdMs) {
                return listOf(
                    AudioChunkWindow(
                        startMs = 0L,
                        endMs = durationMs,
                        index = 0,
                        totalChunks = 1
                    )
                )
            }

            val chunkSizeMs = longAudioChunkSizeMs.coerceAtLeast(60_000L)
            val windows = mutableListOf<AudioChunkWindow>()
            var startMs = 0L
            var index = 0
            while (startMs < durationMs) {
                val endMs = min(startMs + chunkSizeMs, durationMs)
                windows += AudioChunkWindow(
                    startMs = startMs,
                    endMs = endMs,
                    index = index,
                    totalChunks = 0
                )
                startMs = endMs
                index += 1
            }

            val total = windows.size
            return windows.map { chunk -> chunk.copy(totalChunks = total) }
        }
    }
}

class MediaMetadataAudioDurationResolver : AudioDurationResolver {
    override fun resolveDurationMs(audioFile: File): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(audioFile.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }
}

class HeuristicOnDeviceChunkTranscriber : AudioChunkTranscriber {
    override suspend fun transcribeChunk(audioFile: File, chunk: AudioChunkWindow): String {
        return buildString {
            append("Audio segment ")
            append(chunk.index + 1)
            append("/")
            append(chunk.totalChunks)
            append(" processed from ")
            append(formatMs(chunk.startMs))
            append(" to ")
            append(formatMs(chunk.endMs))
            append(".")
        }
    }

    private fun formatMs(value: Long): String {
        val totalSeconds = value / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
