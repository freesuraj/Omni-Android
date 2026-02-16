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
    val documentTitle: String = "",
    val fileType: DocumentFileType = DocumentFileType.TXT,
    val isPremiumUnlocked: Boolean = false,
    val expectedAnalysisCount: Int = 0,
    val analyses: List<AnalysisItemUi> = emptyList(),
    val mode: AnalysisScreenMode = AnalysisScreenMode.CONFIG,
    val progress: DetailedAnalysisProgress = DetailedAnalysisProgress(
        completedCount = 0,
        totalCount = 0,
        label = ""
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
    private val app = application
    private val repository = DetailedAnalysisRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        AnalysisUiState(
            documentTitle = app.getString(R.string.analysis_title_default),
            progress = DetailedAnalysisProgress(
                completedCount = 0,
                totalCount = 0,
                label = app.getString(R.string.analysis_progress_preparing)
            )
        )
    )
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
                            app.getString(R.string.analysis_progress_restarting)
                        } else {
                            app.getString(R.string.analysis_progress_preparing)
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
                            analyses = result.analyses.map { entity -> entity.toUi(fileType, app) },
                            errorMessage = null
                        )
                    }
                }

                DetailedAnalysisGenerationResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            mode = if (it.hasPersistedAnalyses) AnalysisScreenMode.VIEW else AnalysisScreenMode.CONFIG,
                            shouldOpenPaywall = true,
                            errorMessage = app.getString(R.string.analysis_error_requires_premium)
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
            _uiState.update { it.copy(errorMessage = app.getString(R.string.analysis_error_document_not_found)) }
            return
        }

        _uiState.update {
            it.copy(
                documentTitle = bootstrap.documentTitle,
                fileType = bootstrap.fileType,
                isPremiumUnlocked = bootstrap.isPremiumUnlocked,
                expectedAnalysisCount = bootstrap.expectedAnalysisCount,
                analyses = bootstrap.analyses.map { entity ->
                    entity.toUi(bootstrap.fileType, app)
                },
                mode = if (bootstrap.analyses.isEmpty()) AnalysisScreenMode.CONFIG else AnalysisScreenMode.VIEW,
                progress = DetailedAnalysisProgress(
                    completedCount = bootstrap.analyses.size,
                    totalCount = bootstrap.expectedAnalysisCount,
                    label = if (bootstrap.expectedAnalysisCount == 0) {
                        app.getString(R.string.analysis_progress_available_after_processing)
                    } else {
                        app.getString(R.string.analysis_progress_ready_to_analyze)
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

private fun PageAnalysisEntity.toUi(
    fileType: DocumentFileType,
    application: Application
): AnalysisItemUi {
    val sectionTitle = when (fileType) {
        DocumentFileType.PDF -> application.getString(R.string.analysis_section_title_page, pageNumber)
        DocumentFileType.TXT,
        DocumentFileType.WEB,
        DocumentFileType.AUDIO -> application.getString(R.string.analysis_section_title_section, pageNumber)
    }

    return AnalysisItemUi(
        id = id,
        pageNumber = pageNumber,
        title = sectionTitle,
        content = content,
        createdAtEpochMs = createdAtEpochMs
    )
}
