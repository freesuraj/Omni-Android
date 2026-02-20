package com.suraj.apps.omni.core.data.transcription

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAudioTranscriptionEngineTest {

    @Test
    fun transcribeUsesSinglePassAndCompletesProgress() = runBlocking {
        val audioFile = temporaryAudioFile()
        var transcribeCalls = 0
        val progressEvents = mutableListOf<AudioTranscriptionProgress>()
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { 120_000L },
            transcriber = AudioFileTranscriber { _, durationMs, onProgress ->
                transcribeCalls += 1
                assertEquals(120_000L, durationMs)
                onProgress(0.5f)
                "single-pass transcript"
            }
        )

        val result = engine.transcribe(audioFile) { progressEvents += it }

        val success = result as AudioTranscriptionResult.Success
        assertEquals(1, success.chunkCount)
        assertEquals("single-pass transcript", success.transcript)
        assertEquals(1, transcribeCalls)
        assertEquals(0f, progressEvents.first().progress)
        assertEquals(1f, progressEvents.last().progress)
        assertEquals(1, progressEvents.last().chunkIndex)
    }

    @Test
    fun progressIsMonotonicForSinglePassTranscription() = runBlocking {
        val audioFile = temporaryAudioFile()
        val progressEvents = mutableListOf<AudioTranscriptionProgress>()
        val durationMs = 2 * 60 * 1000L
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { durationMs },
            transcriber = AudioFileTranscriber { _, _, onProgress ->
                onProgress(0.2f)
                onProgress(0.8f)
                onProgress(0.6f) // Should be ignored by engine.
                "hello world"
            }
        )

        val result = engine.transcribe(audioFile) { progressEvents += it }

        val success = result as AudioTranscriptionResult.Success
        assertEquals(1, success.chunkCount)
        assertEquals("hello world", success.transcript)
        assertEquals(0f, progressEvents.first().progress)
        assertEquals(1f, progressEvents.last().progress)
        assertTrue(progressEvents.zipWithNext().all { (left, right) -> right.progress >= left.progress })
        assertEquals(durationMs, progressEvents.last().processedDurationMs)
    }

    @Test
    fun transcriptionFailsWhenDurationCannotBeRead() = runBlocking {
        val audioFile = temporaryAudioFile()
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { null },
            transcriber = AudioFileTranscriber { _, _, _ -> "unused" }
        )

        val result = engine.transcribe(audioFile)

        assertTrue(result is AudioTranscriptionResult.Failure)
        val failure = result as AudioTranscriptionResult.Failure
        assertEquals("Unable to read audio duration.", failure.message)
    }

    @Test
    fun transcriptionFailureWrapsThrownError() = runBlocking {
        val audioFile = temporaryAudioFile()
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { 30_000L },
            transcriber = AudioFileTranscriber { _, _, _ ->
                error("boom")
            }
        )

        val result = engine.transcribe(audioFile)

        assertTrue(result is AudioTranscriptionResult.Failure)
        val failure = result as AudioTranscriptionResult.Failure
        assertEquals("Audio transcription failed.", failure.message)
        assertTrue(failure.cause != null)
    }

    @Test
    fun returnsFailureForMissingSpeechContent() = runBlocking {
        val audioFile = temporaryAudioFile()
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { 30_000L },
            transcriber = AudioFileTranscriber { _, _, _ -> "" }
        )

        val result = engine.transcribe(audioFile)

        assertTrue(result is AudioTranscriptionResult.Failure)
        val failure = result as AudioTranscriptionResult.Failure
        assertEquals("No speech content was produced from audio.", failure.message)
    }

    private fun temporaryAudioFile(): File {
        return File.createTempFile("omni-audio-test", ".m4a").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(1, 2, 3))
        }
    }
}
