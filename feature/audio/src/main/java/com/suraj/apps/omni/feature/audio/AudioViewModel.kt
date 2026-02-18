package com.suraj.apps.omni.feature.audio

import android.app.Application
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionResult
import com.suraj.apps.omni.core.data.transcription.OnDeviceAudioTranscriptionEngine
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val WAVEFORM_BAR_COUNT = 28
private const val MIN_WAVE_AMPLITUDE = 0.08f
private const val METER_POLL_MS = 90L
private const val TIMER_POLL_MS = 120L

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    FINALIZING
}

data class AudioUiState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val transcript: String = "",
    val elapsedMs: Long = 0L,
    val waveform: List<Float> = List(WAVEFORM_BAR_COUNT) { MIN_WAVE_AMPLITUDE },
    val shouldRequestMicrophonePermission: Boolean = false,
    val shouldOpenPaywall: Boolean = false,
    val pendingDashboardDocumentId: String? = null,
    val remainingFreeRecordings: Int? = null,
    val errorMessage: String? = null
)

class AudioViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val app = application
    private val appContext = application.applicationContext
    private val repository = DocumentImportRepository(appContext)
    private val fallbackAudioTranscriptionEngine = OnDeviceAudioTranscriptionEngine()

    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var recorderFile: File? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var amplitudeJob: Job? = null
    private var elapsedJob: Job? = null
    private var recordingStartElapsedRealtime = 0L
    private var elapsedBeforeCurrentRunMs = 0L
    private var pendingStartAfterPermission = false
    private var shouldRestartSpeechRecognition = false
    private var committedTranscript = ""
    private var partialTranscript = ""
    private var speechUnavailableReported = false

    init {
        refreshRemainingFreeRecordings()
    }

    fun onRecordTapped(hasMicrophonePermission: Boolean) {
        when (_uiState.value.status) {
            RecordingStatus.IDLE -> startRecordingWithChecks(hasMicrophonePermission)
            RecordingStatus.PAUSED -> resumeRecording()
            RecordingStatus.RECORDING,
            RecordingStatus.FINALIZING -> Unit
        }
    }

    fun onPauseTapped() {
        if (_uiState.value.status != RecordingStatus.RECORDING) return
        val activeRecorder = recorder ?: return

        val pauseResult = runCatching { activeRecorder.pause() }
        if (pauseResult.isFailure) {
            _uiState.update { state ->
                state.copy(errorMessage = app.getString(R.string.audio_error_pause_failed))
            }
            return
        }
        elapsedBeforeCurrentRunMs += SystemClock.elapsedRealtime() - recordingStartElapsedRealtime
        stopAmplitudePolling()
        stopSpeechListening(keepRecognizer = true)
        _uiState.update { it.copy(status = RecordingStatus.PAUSED) }
    }

    fun onFinishTapped() {
        val status = _uiState.value.status
        if (status != RecordingStatus.RECORDING && status != RecordingStatus.PAUSED) return
        finalizeRecording()
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        val shouldStart = pendingStartAfterPermission
        pendingStartAfterPermission = false
        if (!granted) {
            _uiState.update {
                it.copy(errorMessage = app.getString(R.string.audio_error_microphone_permission_required))
            }
            return
        }
        if (shouldStart) {
            startRecordingWithChecks(hasMicrophonePermission = true)
        }
    }

    fun consumePermissionRequest() {
        _uiState.update { it.copy(shouldRequestMicrophonePermission = false) }
    }

    fun consumePaywallNavigation() {
        _uiState.update { it.copy(shouldOpenPaywall = false) }
    }

    fun consumeDashboardNavigation() {
        _uiState.update { it.copy(pendingDashboardDocumentId = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAmplitudePolling()
        stopElapsedTimer()
        releaseRecorder()
        stopSpeechListening(keepRecognizer = false)
        speechUnavailableReported = false
    }

    private fun startRecordingWithChecks(hasMicrophonePermission: Boolean) {
        if (!hasMicrophonePermission) {
            pendingStartAfterPermission = true
            _uiState.update { it.copy(shouldRequestMicrophonePermission = true) }
            return
        }
        val remaining = repository.remainingFreeLiveRecordings()
        if (remaining != Int.MAX_VALUE && remaining <= 0) {
            _uiState.update {
                it.copy(
                    shouldOpenPaywall = true,
                    errorMessage = app.getString(R.string.audio_error_free_recording_limit)
                )
            }
            return
        }
        startRecording()
    }

    private fun startRecording() {
        stopAmplitudePolling()
        stopElapsedTimer()
        releaseRecorder()
        stopSpeechListening(keepRecognizer = false)

        val outputFile = File(
            appContext.cacheDir,
            "live-recording-${System.currentTimeMillis()}.m4a"
        )

        val newRecorder = runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        }.getOrElse {
            _uiState.update {
                it.copy(errorMessage = app.getString(R.string.audio_error_start_failed))
            }
            return
        }

        recorder = newRecorder
        recorderFile = outputFile
        recordingStartElapsedRealtime = SystemClock.elapsedRealtime()
        elapsedBeforeCurrentRunMs = 0L
        committedTranscript = ""
        partialTranscript = ""
        speechUnavailableReported = false
        _uiState.update {
            it.copy(
                status = RecordingStatus.RECORDING,
                transcript = "",
                elapsedMs = 0L,
                waveform = List(WAVEFORM_BAR_COUNT) { MIN_WAVE_AMPLITUDE },
                errorMessage = null
            )
        }
        startAmplitudePolling()
        startElapsedTimer()
        startSpeechListening()
    }

    private fun resumeRecording() {
        val activeRecorder = recorder ?: return
        runCatching {
            activeRecorder.resume()
            recordingStartElapsedRealtime = SystemClock.elapsedRealtime()
        }.onFailure {
            _uiState.update { state ->
                state.copy(errorMessage = app.getString(R.string.audio_error_resume_failed))
            }
            return
        }
        _uiState.update { it.copy(status = RecordingStatus.RECORDING) }
        startAmplitudePolling()
        startSpeechListening()
    }

    private fun finalizeRecording() {
        viewModelScope.launch {
            val wasRecording = _uiState.value.status == RecordingStatus.RECORDING
            _uiState.update { it.copy(status = RecordingStatus.FINALIZING, errorMessage = null) }
            if (wasRecording) {
                elapsedBeforeCurrentRunMs += SystemClock.elapsedRealtime() - recordingStartElapsedRealtime
            }
            stopAmplitudePolling()
            stopElapsedTimer()
            stopSpeechListening(keepRecognizer = false)

            val finalFile = recorderFile
            recorderFile = null
            stopRecorder()
            if (finalFile == null || !finalFile.exists()) {
                resetToIdle(errorMessage = app.getString(R.string.audio_error_recording_file_missing))
                return@launch
            }

            var transcript = _uiState.value.transcript.trim()
            if (transcript.isBlank()) {
                transcript = when (val fallback = fallbackAudioTranscriptionEngine.transcribe(finalFile)) {
                    is AudioTranscriptionResult.Success -> fallback.transcript
                    is AudioTranscriptionResult.Failure -> ""
                }
            }
            if (repository.isPlaceholderAudioTranscript(transcript)) {
                transcript = ""
            }
            when (val result = repository.importLiveRecording(finalFile, transcript)) {
                is DocumentImportResult.Success -> {
                    finalFile.delete()
                    refreshRemainingFreeRecordings()
                    _uiState.update {
                        it.copy(
                            status = RecordingStatus.IDLE,
                            transcript = "",
                            elapsedMs = 0L,
                            waveform = List(WAVEFORM_BAR_COUNT) { MIN_WAVE_AMPLITUDE },
                            pendingDashboardDocumentId = result.documentId
                        )
                    }
                }

                DocumentImportResult.RequiresPremium -> {
                    finalFile.delete()
                    resetToIdle(errorMessage = app.getString(R.string.audio_error_free_recording_limit))
                    _uiState.update { it.copy(shouldOpenPaywall = true) }
                }

                is DocumentImportResult.Failure -> {
                    finalFile.delete()
                    resetToIdle(errorMessage = result.message)
                }
            }
        }
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (true) {
                if (_uiState.value.status != RecordingStatus.RECORDING) break
                val normalized = ((recorder?.maxAmplitude ?: 0) / 32_767f)
                    .coerceIn(0f, 1f)
                    .coerceAtLeast(MIN_WAVE_AMPLITUDE)
                _uiState.update { state ->
                    val updated = (state.waveform.drop(1) + normalized)
                    state.copy(waveform = updated)
                }
                delay(METER_POLL_MS)
            }
        }
    }

    private fun stopAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = null
    }

    private fun startElapsedTimer() {
        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            while (true) {
                val status = _uiState.value.status
                if (status != RecordingStatus.RECORDING && status != RecordingStatus.PAUSED) break
                val elapsed = if (status == RecordingStatus.RECORDING) {
                    elapsedBeforeCurrentRunMs + (SystemClock.elapsedRealtime() - recordingStartElapsedRealtime)
                } else {
                    elapsedBeforeCurrentRunMs
                }
                _uiState.update { it.copy(elapsedMs = elapsed) }
                delay(TIMER_POLL_MS)
            }
        }
    }

    private fun stopElapsedTimer() {
        elapsedJob?.cancel()
        elapsedJob = null
    }

    private fun startSpeechListening() {
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        if (!recognizerAvailable && !onDeviceAvailable) {
            reportSpeechUnavailableOnce()
            return
        }
        if (speechRecognizer == null) {
            // Prefer the standard recognizer first for better emulator/device compatibility.
            val recognizer = if (recognizerAvailable) {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } else if (onDeviceAvailable) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            speechRecognizer = recognizer.also {
                it.setRecognitionListener(speechListener)
            }
        }
        shouldRestartSpeechRecognition = true
        startListeningSafely()
    }

    private fun stopSpeechListening(keepRecognizer: Boolean) {
        shouldRestartSpeechRecognition = false
        speechRecognizer?.let { recognizer ->
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            if (!keepRecognizer) {
                recognizer.destroy()
                speechRecognizer = null
            }
        }
    }

    private fun speechRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Do not force offline-only mode; it fails on many emulators/devices.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }
    }

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            if (!shouldRestartSpeechRecognition) return
            if (_uiState.value.status != RecordingStatus.RECORDING) return
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                return
            }
            viewModelScope.launch {
                delay(350)
                if (shouldRestartSpeechRecognition && _uiState.value.status == RecordingStatus.RECORDING) {
                    startListeningSafely()
                }
            }
        }

        override fun onResults(results: Bundle) {
            commitTranscriptResult(results)
            if (shouldRestartSpeechRecognition && _uiState.value.status == RecordingStatus.RECORDING) {
                startListeningSafely()
            }
        }

        override fun onPartialResults(partialResults: Bundle) {
            val partial = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (partial.isBlank()) return
            partialTranscript = partial
            publishTranscript()
        }
    }

    private fun commitTranscriptResult(results: Bundle) {
        val finalResult = results
            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (finalResult.isBlank()) return

        committedTranscript = if (committedTranscript.isBlank()) {
            finalResult
        } else {
            "$committedTranscript $finalResult"
        }.replace(Regex("\\s+"), " ").trim()
        partialTranscript = ""
        publishTranscript()
    }

    private fun publishTranscript() {
        val combined = buildString {
            if (committedTranscript.isNotBlank()) append(committedTranscript)
            if (partialTranscript.isNotBlank()) {
                if (isNotBlank()) append(' ')
                append(partialTranscript)
            }
        }.replace(Regex("\\s+"), " ").trim()
        _uiState.update { it.copy(transcript = combined) }
    }

    private fun startListeningSafely() {
        runCatching { speechRecognizer?.startListening(speechRecognizerIntent()) }
            .onFailure { reportSpeechUnavailableOnce() }
    }

    private fun reportSpeechUnavailableOnce() {
        if (speechUnavailableReported) return
        speechUnavailableReported = true
        _uiState.update {
            it.copy(errorMessage = app.getString(R.string.audio_error_speech_recognition_unavailable))
        }
    }

    private fun stopRecorder() {
        val activeRecorder = recorder ?: return
        runCatching { activeRecorder.stop() }
        runCatching { activeRecorder.reset() }
        runCatching { activeRecorder.release() }
        recorder = null
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
    }

    private fun resetToIdle(errorMessage: String?) {
        _uiState.update {
            it.copy(
                status = RecordingStatus.IDLE,
                transcript = "",
                elapsedMs = 0L,
                waveform = List(WAVEFORM_BAR_COUNT) { MIN_WAVE_AMPLITUDE },
                errorMessage = errorMessage
            )
        }
    }

    private fun refreshRemainingFreeRecordings() {
        val remaining = repository.remainingFreeLiveRecordings()
        _uiState.update {
            it.copy(
                remainingFreeRecordings = if (remaining == Int.MAX_VALUE) null else remaining
            )
        }
    }
}
