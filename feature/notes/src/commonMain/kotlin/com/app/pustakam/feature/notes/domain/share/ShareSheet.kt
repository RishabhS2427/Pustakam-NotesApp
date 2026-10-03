package com.app.pustakam.feature.notes.domain.share

import com.app.pustakam.core.model.models.share.NoteRole
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.ShareMemberChange
import com.app.pustakam.core.model.models.share.ShareMemberRequest

data class ShareCandidate(val userId: String, val name: String, val handle: String)

data class ShareSheetState(
    val noteId: String = "",
    val query: String = "",
    val searching: Boolean = false,
    val results: List<ShareCandidate> = emptyList(),
    val picked: List<ShareCandidate> = emptyList(),
    val role: NoteRole = NoteRole.EDITOR,
    val shares: List<NoteShare> = emptyList(),
    val busy: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
) {
    val canSend: Boolean get() = picked.isNotEmpty() && !busy

    val members: List<ShareMemberRequest> get() = picked.map { ShareMemberRequest(it.userId, role.name) }

    val roles: List<NoteRole> get() = NoteRole.entries
}

sealed interface ShareSheetIntent {
    data class Open(val noteId: String) : ShareSheetIntent
    data class QueryChanged(val query: String) : ShareSheetIntent
    data class ResultsLoaded(val people: List<ShareCandidate>) : ShareSheetIntent
    data class Picked(val person: ShareCandidate) : ShareSheetIntent
    data class Unpicked(val userId: String) : ShareSheetIntent
    data class RoleChosen(val role: NoteRole) : ShareSheetIntent
    data class SharesLoaded(val shares: List<NoteShare>) : ShareSheetIntent
    data object Sending : ShareSheetIntent
    data class ShareSaved(val share: NoteShare) : ShareSheetIntent
    data class SharingStopped(val shareId: String) : ShareSheetIntent
    data class Failed(val message: String) : ShareSheetIntent
    data object ErrorCleared : ShareSheetIntent
}

object ShareSheetReducer {

    fun reduce(state: ShareSheetState, intent: ShareSheetIntent): ShareSheetState = when (intent) {
        is ShareSheetIntent.Open -> ShareSheetState(noteId = intent.noteId)
        is ShareSheetIntent.QueryChanged -> state.copy(
            query = intent.query,
            searching = intent.query.isNotBlank(),
            results = if (intent.query.isBlank()) emptyList() else state.results
        )
        is ShareSheetIntent.ResultsLoaded -> state.copy(searching = false, results = intent.people)
        is ShareSheetIntent.Picked -> if (state.picked.any { it.userId == intent.person.userId }) state else state.copy(
            picked = state.picked + intent.person,
            query = "",
            results = emptyList(),
            sent = false
        )
        is ShareSheetIntent.Unpicked -> state.copy(picked = state.picked.filterNot { it.userId == intent.userId })
        is ShareSheetIntent.RoleChosen -> state.copy(role = intent.role)
        is ShareSheetIntent.SharesLoaded -> state.copy(shares = intent.shares.filter { ShareSheet.belongsTo(it, state.noteId) })
        ShareSheetIntent.Sending -> state.copy(busy = true, error = null)
        is ShareSheetIntent.ShareSaved -> state.copy(
            busy = false,
            sent = true,
            picked = emptyList(),
            shares = listOf(intent.share) + state.shares.filterNot { it.shareId == intent.share.shareId }
        )
        is ShareSheetIntent.SharingStopped -> state.copy(busy = false, shares = state.shares.filterNot { it.shareId == intent.shareId })
        is ShareSheetIntent.Failed -> state.copy(busy = false, searching = false, error = intent.message)
        ShareSheetIntent.ErrorCleared -> state.copy(error = null)
    }
}

object ShareSheet {

    fun belongsTo(share: NoteShare, noteId: String): Boolean =
        noteId.isNotEmpty() && (share.sourceNoteId == noteId || share.noteId == noteId)

    fun candidate(userId: String, name: String?, username: String?): ShareCandidate = ShareCandidate(
        userId = userId,
        name = name?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: userId,
        handle = username?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
    )

    fun visible(state: ShareSheetState, people: List<ShareCandidate>, me: String): List<ShareCandidate> {
        val taken = state.picked.map { it.userId }.toSet() + me
        return people.filterNot { it.userId in taken }
    }

    fun roleChange(userId: String, role: NoteRole): List<ShareMemberChange> = listOf(ShareMemberChange(userId, role.name))

    fun removal(userId: String): List<ShareMemberChange> = listOf(ShareMemberChange(userId, null))

    fun label(role: NoteRole): String = when (role) {
        NoteRole.READER -> "Can view"
        NoteRole.EDITOR -> "Can edit"
        NoteRole.OWNER -> "Owner"
    }

    fun canManage(share: NoteShare): Boolean = share.access == NoteRole.OWNER
}
