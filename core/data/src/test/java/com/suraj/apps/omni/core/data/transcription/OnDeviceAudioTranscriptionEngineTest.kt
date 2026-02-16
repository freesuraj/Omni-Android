package com.suraj.apps.omni.core.data.transcription

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAudioTranscriptionEngineTest {

    @Test
    fun shortAudioUsesSinglePassAndCompletesProgress() = runBlocking {
        val audioFile = temporaryAudioFile()
        val chunks = mutableListOf<AudioChunkWindow>()
        val progressEvents = mutableListOf<AudioTranscriptionProgress>()
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { 120_000L },
            chunkTranscriber = AudioChunkTranscriber { _, chunk ->
                chunks += chunk
                "chunk-${chunk.index + 1}"
            }
        )

        val result = engine.transcribe(audioFile) { progressEvents += it }

        val success = result as AudioTranscriptionResult.Success
        assertEquals(1, success.chunkCount)
        assertEquals("chunk-1", success.transcript)
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks.first().startMs)
        assertEquals(120_000L, chunks.first().endMs)
        assertEquals(0f, progressEvents.first().progress)
        assertEquals(1f, progressEvents.last().progress)
        assertEquals(1, progressEvents.last().chunkIndex)
    }

    @Test
    fun longAudioIsTranscribedInChunksWithMonotonicProgress() = runBlocking {
        val audioFile = temporaryAudioFile()
        val progressEvents = mutableListOf<AudioTranscriptionProgress>()
        val processedChunkIndexes = mutableListOf<Int>()
        val durationMs = 20 * 60 * 1000L + 30_000L
        val engine = OnDeviceAudioTranscriptionEngine(
            durationResolver = AudioDurationResolver { durationMs },
            chunkTranscriber = AudioChunkTranscriber { _, chunk ->
                processedChunkIndexes += chunk.index
                "seg-${chunk.index + 1}"
            }
        )

        val result = engine.transcribe(audioFile) { progressEvents += it }

        val success = result as AudioTranscriptionResult.Success
        assertEquals(5, success.chunkCount)
        assertEquals("seg-1 seg-2 seg-3 seg-4 seg-5", success.transcript)
        assertEquals(listOf(0, 1, 2, 3, 4), processedChunkIndexes)
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
            chunkTranscriber = AudioChunkTranscriber { _, _ -> "unused" }
        )

        val result = engine.transcribe(audioFile)

        assertTrue(result is AudioTranscriptionResult.Failure)
        val failure = result as AudioTranscriptionResult.Failure
        assertEquals("Unable to read audio duration.", failure.message)
    }

    @Test
    fun chunkBuilderSwitchesBetweenSingleAndBatchedModes() {
        val short = OnDeviceAudioTranscriptionEngine.buildChunks(60_000L)
        val boundary = OnDeviceAudioTranscriptionEngine.buildChunks(10 * 60 * 1000L)
        val long = OnDeviceAudioTranscriptionEngine.buildChunks(10 * 60 * 1000L + 1L)

        assertEquals(1, short.size)
        assertEquals(1, boundary.size)
        assertEquals(3, long.size)
    }

    private fun temporaryAudioFile(): File {
        return File.createTempFile("omni-audio-test", ".m4a").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(1, 2, 3))
        }
    }
}
