package com.suraj.apps.omni.feature.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.local.entity.DocumentSummaryEntity
import com.suraj.apps.omni.core.data.summary.DEFAULT_SUMMARY_WORD_TARGET
import com.suraj.apps.omni.core.data.summary.FREE_MAX_SUMMARY_WORDS
import com.suraj.apps.omni.core.data.summary.MIN_SUMMARY_WORDS
import com.suraj.apps.omni.core.data.summary.PREMIUM_MAX_SUMMARY_WORDS
import com.suraj.apps.omni.core.data.summary.SummaryGenerationResult
import com.suraj.apps.omni.core.data.summary.SummaryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SummaryScreenMode {
    CONFIG,
    GENERATING,
    VIEW
}

data class SummaryUiState(
    val documentTitle: String = "",
    val mode: SummaryScreenMode = SummaryScreenMode.CONFIG,
    val isPremiumUnlocked: Boolean = false,
    val targetWordCount: Int = DEFAULT_SUMMARY_WORD_TARGET,
    val maxWordCount: Int = FREE_MAX_SUMMARY_WORDS,
    val summaries: List<DocumentSummaryEntity> = emptyList(),
    val selectedSummaryId: String? = null,
    val shouldOpenPaywall: Boolean = false,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
) {
    val selectedSummary: DocumentSummaryEntity?
        get() = summaries.firstOrNull { it.id == selectedSummaryId }
}

class SummaryViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val app = application
    private val repository = SummaryRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        SummaryUiState(
            documentTitle = app.getString(R.string.summary_title_configure)
        )
    )
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadBootstrap()
        }
    }

    fun onTargetWordCountChanged(target: Int) {
        _uiState.update { state ->
            state.copy(
                targetWordCount = target.coerceIn(MIN_SUMMARY_WORDS, state.maxWordCount)
            )
        }
    }

    fun generateSummary() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = SummaryScreenMode.GENERATING,
                    isBusy = true,
                    errorMessage = null
                )
            }
            delay(450L)

            when (val result = repository.generateSummary(documentId, _uiState.value.targetWordCount)) {
                is SummaryGenerationResult.Success -> {
                    _uiState.update { state ->
                        val updated = listOf(result.summary) + state.summaries
                        state.copy(
                            mode = SummaryScreenMode.VIEW,
                            summaries = updated,
                            selectedSummaryId = result.summary.id,
                            isBusy = false,
                            errorMessage = null
                        )
                    }
                }

                SummaryGenerationResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            mode = SummaryScreenMode.CONFIG,
                            isBusy = false,
                            shouldOpenPaywall = true,
                            errorMessage = app.getString(R.string.summary_error_requires_premium_length)
                        )
                    }
                }

                is SummaryGenerationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            mode = SummaryScreenMode.CONFIG,
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun selectSummary(summaryId: String) {
        _uiState.update {
            it.copy(
                mode = SummaryScreenMode.VIEW,
                selectedSummaryId = summaryId
            )
        }
    }

    fun backToConfig() {
        _uiState.update {
            it.copy(mode = SummaryScreenMode.CONFIG)
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumePaywallNavigation() {
        _uiState.update { it.copy(shouldOpenPaywall = false) }
    }

    private suspend fun loadBootstrap() {
        val bootstrap = repository.loadBootstrap(documentId)
        if (bootstrap == null) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.summary_error_document_not_found)) }
            return
        }

        val maxWords = if (bootstrap.isPremiumUnlocked) {
            PREMIUM_MAX_SUMMARY_WORDS
        } else {
            FREE_MAX_SUMMARY_WORDS
        }

        _uiState.update {
            it.copy(
                documentTitle = bootstrap.documentTitle,
                isPremiumUnlocked = bootstrap.isPremiumUnlocked,
                targetWordCount = DEFAULT_SUMMARY_WORD_TARGET.coerceIn(MIN_SUMMARY_WORDS, maxWords),
                maxWordCount = maxWords,
                summaries = bootstrap.summaries,
                selectedSummaryId = bootstrap.summaries.firstOrNull()?.id,
                mode = if (bootstrap.summaries.isEmpty()) SummaryScreenMode.CONFIG else SummaryScreenMode.VIEW,
                errorMessage = null,
                isBusy = false
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
                    return SummaryViewModel(application = application, documentId = documentId) as T
                }
            }
        }
    }
}
