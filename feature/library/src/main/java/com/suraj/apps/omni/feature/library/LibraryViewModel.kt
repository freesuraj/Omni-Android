package com.suraj.apps.omni.feature.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.importing.DocumentImportRepository
import com.suraj.apps.omni.core.data.importing.DocumentImportResult
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val isImporting: Boolean = false,
    val showWebDialog: Boolean = false,
    val webUrlInput: String = "",
    val pendingDashboardDocumentId: String? = null,
    val shouldOpenPaywall: Boolean = false,
    val errorMessage: String? = null
)

class LibraryViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = DocumentImportRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDocuments().collect { documents ->
                _uiState.update { it.copy(documents = documents) }
            }
        }
    }

    fun onImportDocument(uri: Uri?) {
        if (uri == null) return
        runImport { repository.importDocument(uri) }
    }

    fun onImportAudio(uri: Uri?) {
        if (uri == null) return
        runImport { repository.importAudio(uri) }
    }

    fun onImportWebArticle() {
        val url = _uiState.value.webUrlInput
        runImport { repository.importWebArticle(url) }
    }

    fun openWebDialog() {
        _uiState.update { it.copy(showWebDialog = true, errorMessage = null) }
    }

    fun dismissWebDialog() {
        _uiState.update { it.copy(showWebDialog = false, webUrlInput = "") }
    }

    fun updateWebUrlInput(value: String) {
        _uiState.update { it.copy(webUrlInput = value) }
    }

    fun consumeDashboardNavigation() {
        _uiState.update { it.copy(pendingDashboardDocumentId = null) }
    }

    fun consumePaywallNavigation() {
        _uiState.update { it.copy(shouldOpenPaywall = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun runImport(action: suspend () -> DocumentImportResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            when (val result = action()) {
                is DocumentImportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            showWebDialog = false,
                            webUrlInput = "",
                            pendingDashboardDocumentId = result.documentId
                        )
                    }
                }

                DocumentImportResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            shouldOpenPaywall = true
                        )
                    }
                }

                is DocumentImportResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}
