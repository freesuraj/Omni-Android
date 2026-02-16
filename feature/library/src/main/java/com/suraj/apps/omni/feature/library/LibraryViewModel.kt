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
    val busyMessage: String? = null,
    val showWebDialog: Boolean = false,
    val webUrlInput: String = "",
    val showRenameDialog: Boolean = false,
    val renameDocumentId: String? = null,
    val renameInput: String = "",
    val showDeleteDialog: Boolean = false,
    val deleteDocumentId: String? = null,
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
        runDocumentMutation(
            busyMessage = "Importing source...",
            navigateToDashboardOnSuccess = true
        ) { repository.importDocument(uri) }
    }

    fun onImportAudio(uri: Uri?) {
        if (uri == null) return
        runDocumentMutation(
            busyMessage = "Importing source...",
            navigateToDashboardOnSuccess = true
        ) { repository.importAudio(uri) }
    }

    fun onImportWebArticle() {
        val url = _uiState.value.webUrlInput
        runDocumentMutation(
            busyMessage = "Importing source...",
            navigateToDashboardOnSuccess = true
        ) { repository.importWebArticle(url) }
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

    fun openRenameDialog(document: DocumentEntity) {
        _uiState.update {
            it.copy(
                showRenameDialog = true,
                renameDocumentId = document.id,
                renameInput = document.title,
                errorMessage = null
            )
        }
    }

    fun dismissRenameDialog() {
        _uiState.update {
            it.copy(
                showRenameDialog = false,
                renameDocumentId = null,
                renameInput = ""
            )
        }
    }

    fun updateRenameInput(value: String) {
        _uiState.update { it.copy(renameInput = value) }
    }

    fun confirmRename() {
        val targetDocumentId = _uiState.value.renameDocumentId ?: return
        val newTitle = _uiState.value.renameInput
        runDocumentMutation(
            busyMessage = "Renaming document..."
        ) { repository.renameDocument(targetDocumentId, newTitle) }
    }

    fun openDeleteDialog(document: DocumentEntity) {
        _uiState.update {
            it.copy(
                showDeleteDialog = true,
                deleteDocumentId = document.id,
                errorMessage = null
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                deleteDocumentId = null
            )
        }
    }

    fun confirmDelete() {
        val targetDocumentId = _uiState.value.deleteDocumentId ?: return
        runDocumentMutation(
            busyMessage = "Deleting document..."
        ) { repository.deleteDocument(targetDocumentId) }
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

    private fun runDocumentMutation(
        busyMessage: String,
        navigateToDashboardOnSuccess: Boolean = false,
        action: suspend () -> DocumentImportResult
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyMessage = busyMessage,
                    errorMessage = null
                )
            }
            when (val result = action()) {
                is DocumentImportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            busyMessage = null,
                            showWebDialog = false,
                            webUrlInput = "",
                            showRenameDialog = false,
                            renameDocumentId = null,
                            renameInput = "",
                            showDeleteDialog = false,
                            deleteDocumentId = null,
                            pendingDashboardDocumentId = if (navigateToDashboardOnSuccess) {
                                result.documentId
                            } else {
                                it.pendingDashboardDocumentId
                            }
                        )
                    }
                }

                DocumentImportResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            busyMessage = null,
                            shouldOpenPaywall = true
                        )
                    }
                }

                is DocumentImportResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            busyMessage = null,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}
