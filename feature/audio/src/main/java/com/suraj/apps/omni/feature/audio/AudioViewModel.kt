package com.suraj.apps.omni.feature.audio

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionResult
import com.suraj.apps.omni.core.data.transcription.LocalAudioTranscriptionEngine
import java.io.File
import java.util.Locale
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
    val errorMessage: String? = null,
    val isLiveTranscriptionAvailable: Boolean = true
)

class AudioViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val app = application
    private val appContext = application.applicationContext
    private val repository = DocumentImportRepository(appContext)
    private val fallbackAudioTranscriptionEngine = LocalAudioTranscriptionEngine()

    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var recorderFile: File? = null
    private var mlKitSpeechRecognizer: com.google.mlkit.genai.speechrecognition.SpeechRecognizer? = null
    private var speechJob: Job? = null
    private var amplitudeJob: Job? = null
    private var elapsedJob: Job? = null
    private var recordingStartElapsedRealtime = 0L
    private var elapsedBeforeCurrentRunMs = 0L
    private var pendingStartAfterPermission = false
    private var committedTranscript = ""
    private var partialTranscript = ""

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
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
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
        _uiState.update {
            it.copy(
                status = RecordingStatus.RECORDING,
                transcript = "",
                elapsedMs = 0L,
                waveform = List(WAVEFORM_BAR_COUNT) { MIN_WAVE_AMPLITUDE },
                errorMessage = null,
                isLiveTranscriptionAvailable = true
            )
        }
        startAmplitudePolling()
        startElapsedTimer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSpeechListening()
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSpeechListening()
        }
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startSpeechListening() {
        speechJob?.cancel()
        partialTranscript = ""
        speechJob = viewModelScope.launch {
            try {
                val client = mlKitSpeechRecognizer ?: run {
                    val options = speechRecognizerOptions {
                        locale = Locale.getDefault()
                        preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
                    }
                    SpeechRecognition.getClient(options).also { mlKitSpeechRecognizer = it }
                }

                val status = client.checkStatus()
                if (status == FeatureStatus.DOWNLOADABLE) {
                    client.download().collect { downloadStatus ->
                        if (downloadStatus is DownloadStatus.DownloadCompleted) {
                            runRecognitionSession(client)
                        } else if (downloadStatus is DownloadStatus.DownloadFailed) {
                            _uiState.update { it.copy(isLiveTranscriptionAvailable = false) }
                        }
                    }
                } else if (status == FeatureStatus.AVAILABLE) {
                    runRecognitionSession(client)
                } else {
                    // Unavailable or downloading
                    _uiState.update { it.copy(isLiveTranscriptionAvailable = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLiveTranscriptionAvailable = false) }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private suspend fun runRecognitionSession(client: com.google.mlkit.genai.speechrecognition.SpeechRecognizer) {
        val request = speechRecognizerRequest {
            audioSource = AudioSource.fromMic()
        }
        _uiState.update { it.copy(isLiveTranscriptionAvailable = true) }
        client.startRecognition(request).collect { response ->
            when (response) {
                is SpeechRecognizerResponse.PartialTextResponse -> {
                    partialTranscript = response.text.trim()
                    publishTranscript()
                }
                is SpeechRecognizerResponse.FinalTextResponse -> {
                    val text = response.text.trim()
                    if (text.isNotBlank()) {
                        committedTranscript = if (committedTranscript.isBlank()) text
                            else "$committedTranscript $text"
                        committedTranscript = committedTranscript.replace(Regex("\\s+"), " ").trim()
                    }
                    partialTranscript = ""
                    publishTranscript()
                }
                is SpeechRecognizerResponse.ErrorResponse -> {
                    // Non-fatal: session may restart
                }
                is SpeechRecognizerResponse.CompletedResponse -> Unit
            }
        }
    }

    private fun stopSpeechListening(keepRecognizer: Boolean) {
        speechJob?.cancel()
        speechJob = null
        if (!keepRecognizer) {
            mlKitSpeechRecognizer?.close()
            mlKitSpeechRecognizer = null
        }
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
