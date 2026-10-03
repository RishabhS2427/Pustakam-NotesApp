package com.app.pustakam.feature.notes.domain.share

import com.app.pustakam.core.model.models.response.notes.NoteSummary
import com.app.pustakam.core.model.models.share.NoteRole

data class ChatShareState(
    val conversationId: String = "",
    val query: String = "",
    val notes: List<NoteSummary> = emptyList(),
    val pickedId: String? = null,
    val role: NoteRole = NoteRole.EDITOR,
    val busy: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
) {
    val canSend: Boolean get() = pickedId != null && !busy

    val roles: List<NoteRole> get() = ChatShareSheet.ROLES
}

sealed interface ChatShareIntent {
    data class Open(val conversationId: String) : ChatShareIntent
    data class QueryChanged(val query: String) : ChatShareIntent
    data class NotesLoaded(val notes: List<NoteSummary>) : ChatShareIntent
    data class Picked(val noteId: String) : ChatShareIntent
    data class RoleChosen(val role: NoteRole) : ChatShareIntent
    data object Sending : ChatShareIntent
    data object Sent : ChatShareIntent
    data class Failed(val message: String) : ChatShareIntent
    data object ErrorCleared : ChatShareIntent
}

object ChatShareReducer {

    fun reduce(state: ChatShareState, intent: ChatShareIntent): ChatShareState = when (intent) {
        is ChatShareIntent.Open -> ChatShareState(conversationId = intent.conversationId)
        is ChatShareIntent.QueryChanged -> state.copy(query = intent.query)
        is ChatShareIntent.NotesLoaded -> state.copy(notes = ChatShareSheet.shareable(intent.notes))
        is ChatShareIntent.Picked -> state.copy(pickedId = intent.noteId.takeUnless { it == state.pickedId })
        is ChatShareIntent.RoleChosen -> state.copy(role = intent.role)
        ChatShareIntent.Sending -> state.copy(busy = true, error = null)
        ChatShareIntent.Sent -> state.copy(busy = false, sent = true)
        is ChatShareIntent.Failed -> state.copy(busy = false, error = intent.message)
        ChatShareIntent.ErrorCleared -> state.copy(error = null)
    }
}

object ChatShareSheet {

    const val UNTITLED = "Untitled note"

    const val NOTE_UNAVAILABLE = "This note is not shared with you any more"

    const val OPEN_NOTE = "Open shared note"

    val ROLES: List<NoteRole> = listOf(NoteRole.READER, NoteRole.EDITOR)

    fun shareable(notes: List<NoteSummary>): List<NoteSummary> = notes.filterNot { it.shared }

    fun title(note: NoteSummary): String = note.title?.takeIf { it.isNotBlank() } ?: UNTITLED
}
