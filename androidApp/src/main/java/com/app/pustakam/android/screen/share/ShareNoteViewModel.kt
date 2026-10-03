package com.app.pustakam.android.screen.share

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.SHARE
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.share.NoteRole
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.StopSharingResult
import com.app.pustakam.feature.chat.domain.usecase.GetChatPeersUseCase
import com.app.pustakam.feature.notes.domain.share.ShareCandidate
import com.app.pustakam.feature.notes.domain.share.ShareSheet
import com.app.pustakam.feature.notes.domain.share.ShareSheetIntent
import com.app.pustakam.feature.notes.domain.share.ShareSheetReducer
import com.app.pustakam.feature.notes.domain.share.ShareSheetState
import com.app.pustakam.feature.notes.domain.usecase.ChangeShareMembersUseCase
import com.app.pustakam.feature.notes.domain.usecase.ListNoteSharesUseCase
import com.app.pustakam.feature.notes.domain.usecase.ShareNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.StopSharingUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject

private const val PEOPLE_SEARCH_DEBOUNCE_MILLIS = 400L

class ShareNoteViewModel : BaseViewModel() {

    private val peersUseCase by inject<GetChatPeersUseCase>()
    private val shareNote by inject<ShareNoteUseCase>()
    private val listShares by inject<ListNoteSharesUseCase>()
    private val changeMembers by inject<ChangeShareMembersUseCase>()
    private val stopSharing by inject<StopSharingUseCase>()

    private val _state = MutableStateFlow(ShareSheetState())
    val state: StateFlow<ShareSheetState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun open(noteId: String) {
        emit(ShareSheetIntent.Open(noteId))
        makeAWish<List<NoteShare>>(SHARE.SHARES, showLoader = false) { listShares() }
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        emit(ShareSheetIntent.QueryChanged(query))
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(PEOPLE_SEARCH_DEBOUNCE_MILLIS)
            makeAWish<List<ChatParticipant>>(SHARE.PEOPLE, showLoader = false) { peersUseCase(query) }
        }
    }

    fun pick(person: ShareCandidate) = emit(ShareSheetIntent.Picked(person))

    fun unpick(userId: String) = emit(ShareSheetIntent.Unpicked(userId))

    fun chooseRole(role: NoteRole) = emit(ShareSheetIntent.RoleChosen(role))

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        emit(ShareSheetIntent.Sending)
        makeAWish<NoteShare>(SHARE.SHARE, showLoader = false) { shareNote(current.noteId, current.members, null, null) }
    }

    fun changeRole(shareId: String, userId: String, role: NoteRole) {
        emit(ShareSheetIntent.Sending)
        makeAWish<NoteShare>(SHARE.MEMBERS, showLoader = false) { changeMembers(shareId, ShareSheet.roleChange(userId, role)) }
    }

    fun remove(shareId: String, userId: String) {
        emit(ShareSheetIntent.Sending)
        makeAWish<NoteShare>(SHARE.MEMBERS, showLoader = false) { changeMembers(shareId, ShareSheet.removal(userId)) }
    }

    fun stop(shareId: String) {
        emit(ShareSheetIntent.Sending)
        makeAWish<StopSharingResult>(SHARE.STOP, showLoader = false) { stopSharing(shareId) }
    }

    private fun emit(intent: ShareSheetIntent) = _state.update { ShareSheetReducer.reduce(it, intent) }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        val data = result.data.data
        when (taskCode) {
            SHARE.PEOPLE -> {
                val people = (data as? List<*>)?.filterIsInstance<ChatParticipant>().orEmpty()
                    .map { ShareSheet.candidate(it.id, it.name, it.username) }
                emit(ShareSheetIntent.ResultsLoaded(ShareSheet.visible(_state.value, people, peersUseCase.currentUserId)))
            }
            SHARE.SHARES -> emit(ShareSheetIntent.SharesLoaded((data as? List<*>)?.filterIsInstance<NoteShare>().orEmpty()))
            SHARE.SHARE, SHARE.MEMBERS -> (data as? NoteShare)?.let { emit(ShareSheetIntent.ShareSaved(it)) }
            SHARE.STOP -> (data as? StopSharingResult)?.let { emit(ShareSheetIntent.SharingStopped(it.shareId)) }
            else -> Unit
        }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        emit(ShareSheetIntent.Failed(error.displayMessage()))
    }

    override fun clearError() = emit(ShareSheetIntent.ErrorCleared)
}
