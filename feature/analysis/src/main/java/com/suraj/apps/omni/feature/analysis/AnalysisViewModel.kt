package com.suraj.apps.omni.feature.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.analysis.DetailedAnalysisGenerationResult
import com.suraj.apps.omni.core.data.analysis.DetailedAnalysisProgress
import com.suraj.apps.omni.core.data.analysis.DetailedAnalysisRepository
import com.suraj.apps.omni.core.data.local.entity.PageAnalysisEntity
import com.suraj.apps.omni.core.model.DocumentFileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnalysisScreenMode {
    CONFIG,
    GENERATING,
    VIEW
}

data class AnalysisItemUi(
    val id: String,
    val pageNumber: Int,
    val title: String,
    val content: String,
    val createdAtEpochMs: Long
)

data class AnalysisUiState(
    val documentTitle: String = "Detailed Analysis",
    val fileType: DocumentFileType = DocumentFileType.TXT,
    val isPremiumUnlocked: Boolean = false,
    val expectedAnalysisCount: Int = 0,
    val analyses: List<AnalysisItemUi> = emptyList(),
    val mode: AnalysisScreenMode = AnalysisScreenMode.CONFIG,
    val progress: DetailedAnalysisProgress = DetailedAnalysisProgress(
        completedCount = 0,
        totalCount = 0,
        label = "Preparing analysis"
    ),
    val shouldOpenPaywall: Boolean = false,
    val errorMessage: String? = null
) {
    val completedCount: Int = analyses.size
    val hasPersistedAnalyses: Boolean = analyses.isNotEmpty()
    val hasPartialAnalyses: Boolean = expectedAnalysisCount > 0 && analyses.size in 1 until expectedAnalysisCount
}

class AnalysisViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val repository = DetailedAnalysisRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadBootstrap()
        }
    }

    fun generateOrResumeAnalysis() {
        runGeneration(retryFromScratch = false)
    }

    fun retryAnalysisFromScratch() {
        runGeneration(retryFromScratch = true)
    }

    fun consumePaywallNavigation() {
        _uiState.update { it.copy(shouldOpenPaywall = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun runGeneration(retryFromScratch: Boolean) {
        if (_uiState.value.mode == AnalysisScreenMode.GENERATING) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = AnalysisScreenMode.GENERATING,
                    errorMessage = null,
                    progress = DetailedAnalysisProgress(
                        completedCount = if (retryFromScratch) 0 else it.completedCount,
                        totalCount = it.expectedAnalysisCount,
                        label = if (retryFromScratch) {
                            "Restarting analysis..."
                        } else {
                            "Preparing analysis..."
                        }
                    )
                )
            }

            when (
                val result = repository.generateAnalysis(
                    documentId = documentId,
                    retryFromScratch = retryFromScratch,
                    onProgress = { progress ->
                        _uiState.update { state -> state.copy(progress = progress) }
                    }
                )
            ) {
                is DetailedAnalysisGenerationResult.Success -> {
                    val fileType = _uiState.value.fileType
                    _uiState.update {
                        it.copy(
                            mode = AnalysisScreenMode.VIEW,
                            expectedAnalysisCount = result.expectedAnalysisCount,
                            analyses = result.analyses.map { entity -> entity.toUi(fileType) },
                            errorMessage = null
                        )
                    }
                }

                DetailedAnalysisGenerationResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            mode = if (it.hasPersistedAnalyses) AnalysisScreenMode.VIEW else AnalysisScreenMode.CONFIG,
                            shouldOpenPaywall = true,
                            errorMessage = "Detailed Analysis is a premium feature. Upgrade to continue."
                        )
                    }
                }

                is DetailedAnalysisGenerationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            mode = if (it.hasPersistedAnalyses) AnalysisScreenMode.VIEW else AnalysisScreenMode.CONFIG,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadBootstrap() {
        val bootstrap = repository.loadBootstrap(documentId)
        if (bootstrap == null) {
            _uiState.update { it.copy(errorMessage = "Document not found.") }
            return
        }

        _uiState.update {
            it.copy(
                documentTitle = bootstrap.documentTitle,
                fileType = bootstrap.fileType,
                isPremiumUnlocked = bootstrap.isPremiumUnlocked,
                expectedAnalysisCount = bootstrap.expectedAnalysisCount,
                analyses = bootstrap.analyses.map { entity -> entity.toUi(bootstrap.fileType) },
                mode = if (bootstrap.analyses.isEmpty()) AnalysisScreenMode.CONFIG else AnalysisScreenMode.VIEW,
                progress = DetailedAnalysisProgress(
                    completedCount = bootstrap.analyses.size,
                    totalCount = bootstrap.expectedAnalysisCount,
                    label = if (bootstrap.expectedAnalysisCount == 0) {
                        "Analysis will be available when processing completes"
                    } else {
                        "Ready to analyze"
                    }
                ),
                errorMessage = null
            )
        }
    }

    companion object {
        fun factory(
            application: Application,
            documentId: String
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnalysisViewModel(application = application, documentId = documentId) as T
                }
            }
        }
    }
}

private fun PageAnalysisEntity.toUi(fileType: DocumentFileType): AnalysisItemUi {
    val sectionTitle = when (fileType) {
        DocumentFileType.PDF -> "Page $pageNumber"
        DocumentFileType.TXT,
        DocumentFileType.WEB,
        DocumentFileType.AUDIO -> "Section $pageNumber"
    }

    return AnalysisItemUi(
        id = id,
        pageNumber = pageNumber,
        title = sectionTitle,
        content = content,
        createdAtEpochMs = createdAtEpochMs
    )
}
