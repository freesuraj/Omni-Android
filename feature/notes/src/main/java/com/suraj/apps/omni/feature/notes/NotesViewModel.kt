package com.suraj.apps.omni.feature.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.local.entity.StudyNoteEntity
import com.suraj.apps.omni.core.data.notes.StudyNotesGenerationResult
import com.suraj.apps.omni.core.data.notes.StudyNotesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotesScreenMode {
    CONFIG,
    GENERATING,
    CARDS
}

data class StudyNoteCardUi(
    val id: String,
    val frontContent: String,
    val backContent: String,
    val isBookmarked: Boolean,
    val colorHex: String,
    val isFlipped: Boolean
)

data class NotesUiState(
    val documentTitle: String = "",
    val mode: NotesScreenMode = NotesScreenMode.CONFIG,
    val notes: List<StudyNoteCardUi> = emptyList(),
    val currentPage: Int = 0,
    val showBookmarkedOnly: Boolean = false,
    val autoReadEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
) {
    val visibleNotes: List<StudyNoteCardUi>
        get() = if (showBookmarkedOnly) notes.filter { it.isBookmarked } else notes
}

class NotesViewModel(
    application: Application,
    private val documentId: String
) : AndroidViewModel(application) {
    private val app = application
    private val repository = StudyNotesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        NotesUiState(
            documentTitle = app.getString(R.string.notes_title_new_flashcards)
        )
    )
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadBootstrap()
        }
    }

    fun generateNotes() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = NotesScreenMode.GENERATING,
                    isBusy = true,
                    errorMessage = null
                )
            }
            delay(500L)

            when (val generation = repository.generateNotes(documentId)) {
                is StudyNotesGenerationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            mode = NotesScreenMode.CARDS,
                            notes = generation.notes.toUiModels(),
                            currentPage = 0,
                            isBusy = false,
                            errorMessage = null
                        )
                    }
                }

                is StudyNotesGenerationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            mode = NotesScreenMode.CONFIG,
                            isBusy = false,
                            errorMessage = generation.message
                        )
                    }
                }
            }
        }
    }

    fun toggleCardFlip(noteId: String) {
        _uiState.update { state ->
            state.copy(
                notes = state.notes.map { note ->
                    if (note.id == noteId) {
                        note.copy(isFlipped = !note.isFlipped)
                    } else {
                        note
                    }
                }
            )
        }
    }

    fun toggleBookmark(noteId: String) {
        viewModelScope.launch {
            val current = _uiState.value.notes.firstOrNull { it.id == noteId } ?: return@launch
            val updated = repository.setBookmark(noteId, isBookmarked = !current.isBookmarked) ?: return@launch
            _uiState.update { state ->
                val refreshed = state.notes.map { note ->
                    if (note.id == noteId) {
                        note.copy(isBookmarked = updated.isBookmarked)
                    } else {
                        note
                    }
                }
                val adjustedPage = normalizeCurrentPage(
                    page = state.currentPage,
                    notes = refreshed,
                    bookmarkedOnly = state.showBookmarkedOnly
                )
                state.copy(notes = refreshed, currentPage = adjustedPage)
            }
        }
    }

    fun setBookmarkedOnly(enabled: Boolean) {
        _uiState.update { state ->
            val adjustedPage = normalizeCurrentPage(
                page = state.currentPage,
                notes = state.notes,
                bookmarkedOnly = enabled
            )
            state.copy(
                showBookmarkedOnly = enabled,
                currentPage = adjustedPage
            )
        }
    }

    fun setAutoReadEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoReadEnabled = enabled) }
    }

    fun setCurrentPage(page: Int) {
        _uiState.update { state ->
            val maxIndex = (state.visibleNotes.size - 1).coerceAtLeast(0)
            state.copy(currentPage = page.coerceIn(0, maxIndex))
        }
    }

    fun backToConfig() {
        _uiState.update {
            it.copy(
                mode = NotesScreenMode.CONFIG,
                currentPage = 0
            )
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun loadBootstrap() {
        val bootstrap = repository.loadBootstrap(documentId)
        if (bootstrap == null) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.notes_error_document_not_found)) }
            return
        }

        _uiState.update {
            it.copy(
                documentTitle = bootstrap.documentTitle,
                notes = bootstrap.notes.toUiModels(),
                mode = if (bootstrap.notes.isEmpty()) NotesScreenMode.CONFIG else NotesScreenMode.CARDS,
                currentPage = 0,
                errorMessage = null,
                isBusy = false
            )
        }
    }

    private fun List<StudyNoteEntity>.toUiModels(): List<StudyNoteCardUi> {
        return sortedBy { it.createdAtEpochMs }.map { note ->
            StudyNoteCardUi(
                id = note.id,
                frontContent = note.frontContent,
                backContent = note.backContent,
                isBookmarked = note.isBookmarked,
                colorHex = note.colorHex,
                isFlipped = false
            )
        }
    }

    private fun normalizeCurrentPage(
        page: Int,
        notes: List<StudyNoteCardUi>,
        bookmarkedOnly: Boolean
    ): Int {
        val visible = if (bookmarkedOnly) notes.filter { it.isBookmarked } else notes
        return page.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
    }

    companion object {
        fun factory(
            application: Application,
            documentId: String
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NotesViewModel(application = application, documentId = documentId) as T
                }
            }
        }
    }
}
