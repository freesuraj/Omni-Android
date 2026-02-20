package com.suraj.apps.omni.core.data.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.zip.ZipInputStream

// Models: https://alphacephei.com/vosk/models (use a "small" variant to keep APK size manageable)
private const val VOSK_MODEL_ASSET = "vosk-model.zip"
private const val VOSK_MODEL_DIR = "vosk-model"
private const val VOSK_SAMPLE_RATE = 16000.0f

class VoskLiveSpeechEngine(private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null

    /**
     * On first run, extracts [VOSK_MODEL_ASSET] from assets into internal storage.
     * On subsequent runs, skips extraction and loads the already-extracted model directly.
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        val modelDir = File(context.filesDir, VOSK_MODEL_DIR)
        if (!modelDir.exists() && !extractAssetZip(VOSK_MODEL_ASSET, modelDir)) {
            return@withContext false
        }
        runCatching {
            model = Model(modelDir.absolutePath)
            true
        }.getOrDefault(false)
    }

    fun isModelLoaded(): Boolean = model != null

    /**
     * Starts continuous listening. Callbacks fire on Vosk internal thread.
     * Returns false if the model is not loaded or if the audio service fails to start.
     */
    fun startListening(
        onPartialResult: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ): Boolean {
        val currentModel = model ?: return false
        return runCatching {
            val recognizer = Recognizer(currentModel, VOSK_SAMPLE_RATE)
            val service = SpeechService(recognizer, VOSK_SAMPLE_RATE)
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val partial = parseHypothesis(hypothesis, "partial")
                    if (partial.isNotBlank()) onPartialResult(partial)
                }

                override fun onResult(hypothesis: String?) {
                    val text = parseHypothesis(hypothesis, "text")
                    if (text.isNotBlank()) onResult(text)
                }

                // Called when stopListening() is invoked — captures any trailing speech.
                override fun onFinalResult(hypothesis: String?) {
                    val text = parseHypothesis(hypothesis, "text")
                    if (text.isNotBlank()) onResult(text)
                }

                override fun onError(exception: Exception?) {
                    if (exception != null) onError(exception)
                }

                override fun onTimeout() = Unit
            })
            speechService = service
            true
        }.getOrElse { e ->
            if (e is Exception) onError(e)
            false
        }
    }

    /** Stops the current recognition session. The model remains loaded for reuse on resume. */
    fun stopListening() {
        runCatching { speechService?.stop() }
        runCatching { speechService?.shutdown() }
        speechService = null
    }

    /** Releases all resources. Call from ViewModel.onCleared(). */
    fun destroy() {
        stopListening()
        runCatching { model?.close() }
        model = null
    }

    /**
     * Extracts a zip from assets into [destDir], stripping the single top-level directory
     * that Vosk model zips contain (e.g. "vosk-model-small-en-us-0.15/").
     * Extracts into a temp directory first, then renames atomically so a partial extraction
     * from a previous failed run never leaves a corrupt [destDir].
     */
    private fun extractAssetZip(assetName: String, destDir: File): Boolean {
        val tempDir = File(destDir.parent, "${destDir.name}.tmp")
        tempDir.deleteRecursively()
        val success = runCatching {
            tempDir.mkdirs()
            context.assets.open(assetName).use { assetStream ->
                ZipInputStream(assetStream).use { zip ->
                    // Detect the top-level directory prefix by reading the first directory entry.
                    var prefix = ""
                    var entry = zip.nextEntry
                    if (entry != null && entry.isDirectory) {
                        prefix = entry.name  // e.g. "vosk-model-small-en-us-0.15/"
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    while (entry != null) {
                        val relativeName = entry.name.removePrefix(prefix)
                        if (relativeName.isNotEmpty()) {
                            val outFile = File(tempDir, relativeName)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out -> zip.copyTo(out) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            tempDir.renameTo(destDir)
        }.getOrDefault(false)
        if (!success) tempDir.deleteRecursively()
        return success
    }

    private fun parseHypothesis(hypothesis: String?, key: String): String {
        if (hypothesis.isNullOrBlank()) return ""
        return runCatching {
            JSONObject(hypothesis).optString(key, "").trim()
        }.getOrDefault("")
    }
}
