package com.app.pustakam.android.screen.share

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.SHARE
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.response.notes.NoteSummary
import com.app.pustakam.core.model.models.share.NoteRole
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.feature.notes.domain.share.ChatShareIntent
import com.app.pustakam.feature.notes.domain.share.ChatShareReducer
import com.app.pustakam.feature.notes.domain.share.ChatShareState
import com.app.pustakam.feature.notes.domain.usecase.GetNoteSummariesUseCase
import com.app.pustakam.feature.notes.domain.usecase.SearchNotesUseCase
import com.app.pustakam.feature.notes.domain.usecase.ShareNoteUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject

private const val NOTE_SEARCH_DEBOUNCE_MILLIS = 300L
private const val FIRST_PAGE = 1
private const val PAGE_SIZE = 50

class ChatShareViewModel : BaseViewModel() {

    private val summaries by inject<GetNoteSummariesUseCase>()
    private val searchNotes by inject<SearchNotesUseCase>()
    private val shareNote by inject<ShareNoteUseCase>()

    private val _state = MutableStateFlow(ChatShareState())
    val state: StateFlow<ChatShareState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var notesJob: Job? = null
    private var latest: List<NoteSummary> = emptyList()
    private var requestedFirstPage = false

    fun open(conversationId: String) {
        emit(ChatShareIntent.Open(conversationId))
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            summaries.noteSummaries.collect { loaded ->
                latest = loaded
                if (_state.value.query.isBlank()) emit(ChatShareIntent.NotesLoaded(loaded))
                if (loaded.isEmpty() && !requestedFirstPage) {
                    requestedFirstPage = true
                    makeAWish<List<NoteSummary>>(SHARE.NOTE_PAGE, showLoader = false) { summaries(FIRST_PAGE, PAGE_SIZE) }
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        emit(ChatShareIntent.QueryChanged(query))
        if (query.isBlank()) return emit(ChatShareIntent.NotesLoaded(latest))
        searchJob = viewModelScope.launch {
            delay(NOTE_SEARCH_DEBOUNCE_MILLIS)
            makeAWish<List<NoteSummary>>(SHARE.NOTES, showLoader = false) { searchNotes(query) }
        }
    }

    fun pick(noteId: String) = emit(ChatShareIntent.Picked(noteId))

    fun chooseRole(role: NoteRole) = emit(ChatShareIntent.RoleChosen(role))

    fun send() {
        val current = _state.value
        val noteId = current.pickedId ?: return
        if (!current.canSend) return
        emit(ChatShareIntent.Sending)
        makeAWish<NoteShare>(SHARE.SHARE, showLoader = false) {
            shareNote(noteId, emptyList(), current.conversationId, current.role.name)
        }
    }

    private fun emit(intent: ChatShareIntent) = _state.update { ChatShareReducer.reduce(it, intent) }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        when (taskCode) {
            SHARE.NOTES -> emit(ChatShareIntent.NotesLoaded((result.data.data as? List<*>)?.filterIsInstance<NoteSummary>().orEmpty()))
            SHARE.SHARE -> emit(ChatShareIntent.Sent)
            else -> Unit
        }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        emit(ChatShareIntent.Failed(error.displayMessage()))
    }

    override fun clearError() = emit(ChatShareIntent.ErrorCleared)
}
