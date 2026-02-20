package com.suraj.apps.omni.core.data.transcription

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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

fun interface AudioDurationResolver {
    fun resolveDurationMs(audioFile: File): Long?
}

fun interface AudioFileTranscriber {
    suspend fun transcribe(
        audioFile: File,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): String
}

class OnDeviceAudioTranscriptionEngine(
    private val transcriber: AudioFileTranscriber,
    private val durationResolver: AudioDurationResolver = MediaMetadataAudioDurationResolver()
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

        onProgress(
            AudioTranscriptionProgress(
                progress = 0f,
                processedDurationMs = 0L,
                totalDurationMs = durationMs,
                chunkIndex = 0,
                chunkCount = 1
            )
        )

        var lastProgress = 0f
        val rawTranscript = runCatching {
            transcriber.transcribe(audioFile, durationMs) { transcriberProgress ->
                val normalizedProgress = transcriberProgress.coerceIn(0f, 1f)
                if (normalizedProgress <= lastProgress) return@transcribe
                lastProgress = normalizedProgress
                val processedDuration = (durationMs * normalizedProgress).toLong().coerceAtMost(durationMs)
                onProgress(
                    AudioTranscriptionProgress(
                        progress = normalizedProgress,
                        processedDurationMs = processedDuration,
                        totalDurationMs = durationMs,
                        chunkIndex = 1,
                        chunkCount = 1
                    )
                )
            }
        }.getOrElse { error ->
            return AudioTranscriptionResult.Failure(
                message = "Audio transcription failed.",
                cause = error
            )
        }

        val transcript = rawTranscript.replace(Regex("\\s+"), " ").trim()
        if (transcript.isEmpty()) {
            return AudioTranscriptionResult.Failure("No speech content was produced from audio.")
        }

        onProgress(
            AudioTranscriptionProgress(
                progress = 1f,
                processedDurationMs = durationMs,
                totalDurationMs = durationMs,
                chunkIndex = 1,
                chunkCount = 1
            )
        )

        return AudioTranscriptionResult.Success(
            transcript = transcript,
            durationMs = durationMs,
            chunkCount = 1
        )
    }
}

class MediaMetadataAudioDurationResolver : AudioDurationResolver {
    override fun resolveDurationMs(audioFile: File): Long? {
        val retrieverDuration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(audioFile.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (retrieverDuration != null && retrieverDuration > 0L) {
            return retrieverDuration
        }

        return runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(audioFile.absolutePath)
                var maxDurationUs = 0L
                for (trackIndex in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(trackIndex)
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        val trackDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                        if (trackDurationUs > maxDurationUs) {
                            maxDurationUs = trackDurationUs
                        }
                    }
                }
                if (maxDurationUs > 0L) maxDurationUs / 1000L else null
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrNull()
    }
}

class VoskAudioFileTranscriber(
    private val voskEngine: VoskLiveSpeechEngine
) : AudioFileTranscriber {
    override suspend fun transcribe(
        audioFile: File,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): String {
        if (!voskEngine.isModelLoaded()) {
            voskEngine.loadModel()
        }
        return voskEngine.transcribeChunk(
            audioFile = audioFile,
            startMs = 0L,
            endMs = durationMs,
            onProgress = onProgress
        )
    }
}
