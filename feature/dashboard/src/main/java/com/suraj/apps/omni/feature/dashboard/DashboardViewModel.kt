package com.suraj.apps.omni.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.transcription.AudioTranscriptionResult
import com.suraj.apps.omni.core.model.DocumentFileType
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
    val onboardingLabel: String = "Preparing onboarding",
    val onboardingProgress: Float = 0f,
    val errorMessage: String? = null
)

class DashboardViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val repository = DocumentImportRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var onboardingJob: Job? = null
    private var activeOnboardingDocumentId: String? = null

    init {
        viewModelScope.launch {
            repository.observeDocument(documentId).collect { document ->
                if (document == null) {
                    _uiState.update {
                        it.copy(
                            document = null,
                            errorMessage = "Document not found."
                        )
                    }
                    return@collect
                }

                val statusView = statusView(document)
                _uiState.update {
                    it.copy(
                        document = document,
                        isOnboarding = document.isOnboarding,
                        onboardingLabel = statusView.label,
                        onboardingProgress = statusView.progress,
                        errorMessage = if (document.onboardingStatus == "transcription_failed") {
                            "Audio transcription failed. Retry onboarding."
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
                        errorMessage = error.message ?: "Onboarding failed.",
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
                    onboardingLabel = "Transcribing audio ($percent%)",
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
                        onboardingLabel = "Transcription failed",
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
                    onboardingLabel = "Generating study outputs ($percent%)",
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
                onboardingLabel = "Ready to study",
                onboardingProgress = 1f,
                errorMessage = null
            )
        }
    }

    private fun statusView(document: DocumentEntity): StatusView {
        val status = document.onboardingStatus.orEmpty()
        if (!document.isOnboarding || status == "ready") {
            return StatusView(label = "Ready to study", progress = 1f)
        }
        if (status == "transcribed") {
            return StatusView(label = "Transcription complete", progress = 0.6f)
        }
        if (status == "transcription_failed") {
            return StatusView(label = "Transcription failed", progress = 0f)
        }
        if (status.startsWith("transcribing:")) {
            val percent = status.substringAfter("transcribing:").toIntOrNull()?.coerceIn(0, 100) ?: 0
            return StatusView(
                label = "Transcribing audio ($percent%)",
                progress = (percent / 100f) * 0.6f
            )
        }
        if (status.startsWith("processing:")) {
            val percent = status.substringAfter("processing:").toIntOrNull()?.coerceIn(0, 100) ?: 0
            return StatusView(
                label = "Generating study outputs ($percent%)",
                progress = percent / 100f
            )
        }
        return StatusView(label = "Preparing onboarding", progress = 0.05f)
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
