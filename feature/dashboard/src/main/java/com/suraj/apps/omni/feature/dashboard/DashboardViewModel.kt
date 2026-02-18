package com.suraj.apps.omni.feature.dashboard

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionResult
import com.suraj.apps.omni.core.model.DocumentFileType
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val document: DocumentEntity? = null,
    val isOnboarding: Boolean = false,
    val showRetryAction: Boolean = false,
    val onboardingLabel: String = "",
    val onboardingProgress: Float = 0f,
    val sourceStats: String = "",
    val sourceUrl: String? = null,
    val latestQuizQuestionCount: Int = 0,
    val studyNoteCount: Int = 0,
    val audioSourcePath: String? = null,
    val audioTranscript: String = "",
    val isPremiumUnlocked: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val app = application
    private val repository = DocumentImportRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(
        DashboardUiState(
            onboardingLabel = app.getString(R.string.dashboard_status_preparing_onboarding)
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var onboardingJob: Job? = null
    private var activeOnboardingDocumentId: String? = null

    init {
        viewModelScope.launch {
            repository.observeLatestQuizQuestionCount(documentId).collect { count ->
                _uiState.update { it.copy(latestQuizQuestionCount = count.coerceAtLeast(0)) }
            }
        }
        viewModelScope.launch {
            repository.observeStudyNoteCount(documentId).collect { count ->
                _uiState.update { it.copy(studyNoteCount = count.coerceAtLeast(0)) }
            }
        }
        viewModelScope.launch {
            repository.observeDocument(documentId).collect { document ->
                if (document == null) {
                    _uiState.update {
                        it.copy(
                            document = null,
                            errorMessage = app.getString(R.string.dashboard_error_document_not_found)
                        )
                    }
                    return@collect
                }

                val fullText = repository.readFullText(document.id).orEmpty()
                val visibleAudioTranscript = if (
                    document.fileType == DocumentFileType.AUDIO &&
                    repository.isPlaceholderAudioTranscript(fullText)
                ) {
                    ""
                } else {
                    fullText
                }
                val audioSourcePath = resolveLocalSourcePath(document)
                val sourceStats = buildSourceStats(
                    document = document,
                    fullText = if (document.fileType == DocumentFileType.AUDIO) {
                        visibleAudioTranscript
                    } else {
                        fullText
                    },
                    audioPath = audioSourcePath
                )
                val statusView = statusView(document)
                _uiState.update {
                    it.copy(
                        document = document,
                        isOnboarding = document.isOnboarding,
                        showRetryAction = document.onboardingStatus == "transcription_failed",
                        onboardingLabel = statusView.label,
                        onboardingProgress = statusView.progress,
                        sourceStats = sourceStats,
                        sourceUrl = document.sourceUrl,
                        audioSourcePath = if (document.fileType == DocumentFileType.AUDIO) {
                            audioSourcePath
                        } else {
                            null
                        },
                        audioTranscript = if (document.fileType == DocumentFileType.AUDIO) {
                            visibleAudioTranscript
                        } else {
                            ""
                        },
                        isPremiumUnlocked = repository.isPremiumUnlocked(),
                        errorMessage = if (document.onboardingStatus == "transcription_failed") {
                            app.getString(R.string.dashboard_error_audio_transcription_failed_retry)
                        } else {
                            it.errorMessage
                        }
                    )
                }
                maybeStartOnboarding(document)
            }
        }
    }

    fun retryOnboarding() {
        onboardingJob?.cancel()
        activeOnboardingDocumentId = null
        val currentDocument = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.updateOnboardingState(
                documentId = currentDocument.id,
                isOnboarding = true,
                onboardingStatus = "imported"
            )
            maybeStartOnboarding(currentDocument.copy(isOnboarding = true, onboardingStatus = "imported"))
        }
    }

    fun requestPremiumFeatureAccess(featureName: String): Boolean {
        val latestPremiumState = repository.isPremiumUnlocked()
        if (latestPremiumState != _uiState.value.isPremiumUnlocked) {
            _uiState.update { it.copy(isPremiumUnlocked = latestPremiumState) }
        }
        if (latestPremiumState) return true
        _uiState.update {
            it.copy(errorMessage = app.getString(R.string.dashboard_error_premium_feature, featureName))
        }
        return false
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun maybeStartOnboarding(document: DocumentEntity) {
        if (!document.isOnboarding) return
        if (activeOnboardingDocumentId == document.id && onboardingJob?.isActive == true) return

        onboardingJob?.cancel()
        activeOnboardingDocumentId = document.id
        onboardingJob = viewModelScope.launch {
            runCatching {
                when (document.fileType) {
                    DocumentFileType.AUDIO -> runAudioOnboarding(document.id)
                    else -> runProcessingOnboarding(document.id, startProgress = 0f)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message ?: app.getString(R.string.dashboard_error_onboarding_failed),
                        isOnboarding = true
                    )
                }
            }
            activeOnboardingDocumentId = null
        }
    }

    private suspend fun runAudioOnboarding(documentId: String) {
        val transcriptionResult = repository.transcribeAudioDocument(documentId) { progress ->
            val percent = (progress.progress * 100f).toInt().coerceIn(0, 100)
            val overallProgress = progress.progress * 0.6f
            _uiState.update {
                it.copy(
                        isOnboarding = true,
                        showRetryAction = false,
                        onboardingLabel = app.getString(R.string.dashboard_status_transcribing_audio, percent),
                        onboardingProgress = overallProgress,
                        errorMessage = null
                )
            }
            viewModelScope.launch {
                repository.updateOnboardingState(
                    documentId = documentId,
                    isOnboarding = true,
                    onboardingStatus = "transcribing:$percent"
                )
            }
        }

        when (transcriptionResult) {
            is AudioTranscriptionResult.Failure -> {
                repository.updateOnboardingState(
                    documentId = documentId,
                    isOnboarding = true,
                    onboardingStatus = "transcription_failed"
                )
                _uiState.update {
                    it.copy(
                        isOnboarding = true,
                        showRetryAction = true,
                        onboardingLabel = app.getString(R.string.dashboard_status_transcription_failed),
                        errorMessage = transcriptionResult.message
                    )
                }
                return
            }

            is AudioTranscriptionResult.Success -> {
                runProcessingOnboarding(documentId, startProgress = 0.6f)
            }
        }
    }

    private suspend fun runProcessingOnboarding(
        documentId: String,
        startProgress: Float
    ) {
        val totalSteps = 5
        for (step in 1..totalSteps) {
            val stageProgress = step.toFloat() / totalSteps.toFloat()
            val overallProgress = startProgress + (1f - startProgress) * stageProgress
            val percent = (overallProgress * 100f).toInt().coerceIn(0, 100)
            _uiState.update {
                it.copy(
                    isOnboarding = true,
                    showRetryAction = false,
                    onboardingLabel = app.getString(R.string.dashboard_status_generating_outputs, percent),
                    onboardingProgress = overallProgress,
                    errorMessage = null
                )
            }
            repository.updateOnboardingState(
                documentId = documentId,
                isOnboarding = true,
                onboardingStatus = "processing:$percent"
            )
            delay(350L)
        }

        repository.updateOnboardingState(
            documentId = documentId,
            isOnboarding = false,
            onboardingStatus = "ready"
        )
        _uiState.update {
            it.copy(
                isOnboarding = false,
                showRetryAction = false,
                onboardingLabel = app.getString(R.string.dashboard_status_ready_to_study),
                onboardingProgress = 1f,
                errorMessage = null
            )
        }
    }

    private fun statusView(document: DocumentEntity): StatusView {
        val status = document.onboardingStatus.orEmpty()
        if (!document.isOnboarding || status == "ready") {
            return StatusView(label = app.getString(R.string.dashboard_status_ready_to_study), progress = 1f)
        }
        if (status == "transcribed") {
            return StatusView(label = app.getString(R.string.dashboard_status_transcription_complete), progress = 0.6f)
        }
        if (status == "transcription_failed") {
            return StatusView(label = app.getString(R.string.dashboard_status_transcription_failed), progress = 0f)
        }
        if (status.startsWith("transcribing:")) {
            val percent = status.substringAfter("transcribing:").toIntOrNull()?.coerceIn(0, 100) ?: 0
            return StatusView(
                label = app.getString(R.string.dashboard_status_transcribing_audio, percent),
                progress = (percent / 100f) * 0.6f
            )
        }
        if (status.startsWith("processing:")) {
            val percent = status.substringAfter("processing:").toIntOrNull()?.coerceIn(0, 100) ?: 0
            return StatusView(
                label = app.getString(R.string.dashboard_status_generating_outputs, percent),
                progress = percent / 100f
            )
        }
        return StatusView(label = app.getString(R.string.dashboard_status_preparing_onboarding), progress = 0.05f)
    }

    private fun resolveLocalSourcePath(document: DocumentEntity): String? {
        val path = document.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
        if (path.isBlank()) return null
        return path.takeIf { File(it).exists() }
    }

    private fun buildSourceStats(
        document: DocumentEntity,
        fullText: String,
        audioPath: String?
    ): String {
        val wordCount = fullText
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size
        return when (document.fileType) {
            DocumentFileType.PDF -> app.getString(
                R.string.dashboard_source_stats_pdf,
                wordCount.coerceAtLeast(1)
            )
            DocumentFileType.TXT -> app.getString(
                R.string.dashboard_source_stats_text,
                wordCount.coerceAtLeast(1)
            )
            DocumentFileType.WEB -> {
                val host = document.sourceUrl
                    ?.let { Uri.parse(it).host }
                    .orEmpty()
                    .ifBlank { app.getString(R.string.dashboard_web_article_fallback_host) }
                app.getString(
                    R.string.dashboard_source_stats_web,
                    host,
                    wordCount.coerceAtLeast(1)
                )
            }

            DocumentFileType.AUDIO -> {
                val durationLabel = resolveAudioDurationLabel(audioPath)
                if (wordCount <= 0) {
                    app.getString(
                        R.string.dashboard_source_stats_audio_pending,
                        durationLabel
                    )
                } else {
                    app.getString(
                        R.string.dashboard_source_stats_audio,
                        durationLabel,
                        wordCount
                    )
                }
            }
        }
    }

    private fun resolveAudioDurationLabel(audioPath: String?): String {
        if (audioPath.isNullOrBlank()) return app.getString(R.string.dashboard_duration_unavailable)
        val durationMs = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(audioPath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (durationMs == null || durationMs <= 0L) {
            return app.getString(R.string.dashboard_duration_unavailable)
        }
        val totalSeconds = (durationMs / 1000.0).roundToInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    companion object {
        fun factory(
            application: Application,
            documentId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(application, documentId) as T
            }
        }
    }
}

private data class StatusView(
    val label: String,
    val progress: Float
)
