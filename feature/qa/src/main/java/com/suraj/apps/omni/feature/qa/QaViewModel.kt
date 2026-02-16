package com.suraj.apps.omni.feature.qa

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.local.entity.QaMessageEntity
import com.suraj.apps.omni.core.data.qa.QaAskResult
import com.suraj.apps.omni.core.data.qa.QaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QaMessageUi(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val isError: Boolean,
    val createdAtEpochMs: Long
)

data class QaUiState(
    val documentTitle: String = "Q&A",
    val messages: List<QaMessageUi> = emptyList(),
    val draftQuestion: String = "",
    val isSending: Boolean = false,
    val shouldOpenPaywall: Boolean = false,
    val errorMessage: String? = null
)

class QaViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val repository = QaRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(QaUiState())
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadBootstrap()
        }
    }

    fun onDraftChanged(value: String) {
        _uiState.update { it.copy(draftQuestion = value.take(MAX_DRAFT_LENGTH)) }
    }

    fun sendQuestion() {
        if (_uiState.value.isSending) return

        val question = _uiState.value.draftQuestion
            .replace(Regex("\\s+"), " ")
            .trim()
        if (question.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a question first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    errorMessage = null
                )
            }

            when (val result = repository.askQuestion(documentId = documentId, question = question)) {
                is QaAskResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages + listOf(
                                result.userMessage.toUi(),
                                result.assistantMessage.toUi()
                            ),
                            draftQuestion = "",
                            isSending = false
                        )
                    }
                }

                QaAskResult.RequiresPremium -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            shouldOpenPaywall = true,
                            errorMessage = "Q&A is a premium feature. Upgrade to continue."
                        )
                    }
                }

                is QaAskResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun consumePaywallNavigation() {
        _uiState.update { it.copy(shouldOpenPaywall = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
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
                messages = bootstrap.messages.map { entity -> entity.toUi() },
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
                    return QaViewModel(application = application, documentId = documentId) as T
                }
            }
        }
    }
}

private fun QaMessageEntity.toUi(): QaMessageUi {
    return QaMessageUi(
        id = id,
        content = content,
        isUser = isUser,
        isError = isError,
        createdAtEpochMs = createdAtEpochMs
    )
}

private const val MAX_DRAFT_LENGTH = 280
