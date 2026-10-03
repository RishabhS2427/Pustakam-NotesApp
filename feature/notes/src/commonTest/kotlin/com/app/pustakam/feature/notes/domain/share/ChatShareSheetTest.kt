package com.app.pustakam.feature.notes.domain.share

import com.app.pustakam.core.model.models.response.notes.NoteSummary
import com.app.pustakam.core.model.models.share.NoteRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatShareSheetTest {

    private val trip = NoteSummary(id = "n1", title = "Trip")
    private val blank = NoteSummary(id = "n2", title = " ")
    private val copy = NoteSummary(id = "n3", title = "Shared with me", shared = true)

    private fun reduce(vararg intents: ChatShareIntent): ChatShareState =
        intents.fold(ChatShareState()) { state, intent -> ChatShareReducer.reduce(state, intent) }

    @Test
    fun onlyMyOwnNotesCanBeSharedIntoAChat() {
        val state = reduce(ChatShareIntent.Open("c1"), ChatShareIntent.NotesLoaded(listOf(trip, blank, copy)))
        assertEquals(listOf("n1", "n2"), state.notes.map { it.id })
        assertEquals("Untitled note", ChatShareSheet.title(blank))
        assertEquals(listOf(NoteRole.READER, NoteRole.EDITOR), state.roles)
        assertFalse(state.canSend)
    }

    @Test
    fun aPickedNoteIsSentOnceWithTheChosenRole() {
        val picked = reduce(ChatShareIntent.Open("c1"), ChatShareIntent.Picked("n1"), ChatShareIntent.RoleChosen(NoteRole.READER))
        assertTrue(picked.canSend)
        assertEquals(NoteRole.READER, picked.role)
        val sending = ChatShareReducer.reduce(picked, ChatShareIntent.Sending)
        assertFalse(sending.canSend)
        assertTrue(ChatShareReducer.reduce(sending, ChatShareIntent.Sent).sent)
        assertNull(ChatShareReducer.reduce(picked, ChatShareIntent.Picked("n1")).pickedId)
    }

    @Test
    fun aFailureFreesTheSheetAndCanBeDismissed() {
        val failed = reduce(ChatShareIntent.Open("c1"), ChatShareIntent.Picked("n1"), ChatShareIntent.Sending, ChatShareIntent.Failed("offline"))
        assertTrue(failed.canSend)
        assertEquals("offline", failed.error)
        assertNull(ChatShareReducer.reduce(failed, ChatShareIntent.ErrorCleared).error)
    }
}
