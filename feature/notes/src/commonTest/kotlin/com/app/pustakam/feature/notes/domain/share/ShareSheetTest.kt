package com.app.pustakam.feature.notes.domain.share

import com.app.pustakam.core.model.models.share.NoteRole
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.ShareMemberChange
import com.app.pustakam.core.model.models.share.ShareMemberRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareSheetTest {

    private val ravi = ShareSheet.candidate("u-ravi", "Ravi", "ravi")
    private val mira = ShareSheet.candidate("u-mira", null, "mira")

    private fun reduce(vararg intents: ShareSheetIntent): ShareSheetState =
        intents.fold(ShareSheetState()) { state, intent -> ShareSheetReducer.reduce(state, intent) }

    @Test
    fun peopleArePickedOnceAndSharedWithTheChosenRole() {
        val state = reduce(
            ShareSheetIntent.Open("n1"),
            ShareSheetIntent.QueryChanged("ra"),
            ShareSheetIntent.ResultsLoaded(listOf(ravi, mira)),
            ShareSheetIntent.Picked(ravi),
            ShareSheetIntent.Picked(ravi),
            ShareSheetIntent.Picked(mira),
            ShareSheetIntent.RoleChosen(NoteRole.READER)
        )
        assertEquals(listOf("u-ravi", "u-mira"), state.picked.map { it.userId })
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertEquals(listOf(ShareMemberRequest("u-ravi", "READER"), ShareMemberRequest("u-mira", "READER")), state.members)
        assertTrue(state.canSend)
        assertFalse(ShareSheetReducer.reduce(state, ShareSheetIntent.Sending).canSend)
    }

    @Test
    fun aSavedShareClearsThePickAndListsTheShare() {
        val share = NoteShare(shareId = "s1", noteId = "copy", sourceNoteId = "n1", role = "OWNER")
        val state = reduce(
            ShareSheetIntent.Open("n1"),
            ShareSheetIntent.Picked(ravi),
            ShareSheetIntent.Sending,
            ShareSheetIntent.ShareSaved(share)
        )
        assertTrue(state.sent)
        assertTrue(state.picked.isEmpty())
        assertEquals(listOf("s1"), state.shares.map { it.shareId })
        assertTrue(ShareSheetReducer.reduce(state, ShareSheetIntent.SharingStopped("s1")).shares.isEmpty())
    }

    @Test
    fun onlyThisNotesSharesAreListed() {
        val mine = NoteShare(shareId = "s1", noteId = "copy-1", sourceNoteId = "n1")
        val copyItself = NoteShare(shareId = "s2", noteId = "n1", sourceNoteId = "n0")
        val other = NoteShare(shareId = "s3", noteId = "copy-9", sourceNoteId = "n9")
        val state = reduce(ShareSheetIntent.Open("n1"), ShareSheetIntent.SharesLoaded(listOf(mine, copyItself, other)))
        assertEquals(listOf("s1", "s2"), state.shares.map { it.shareId })
    }

    @Test
    fun aFailureStopsTheSpinnerAndCanBeDismissed() {
        val failed = reduce(ShareSheetIntent.Open("n1"), ShareSheetIntent.QueryChanged("x"), ShareSheetIntent.Sending, ShareSheetIntent.Failed("No network"))
        assertFalse(failed.busy)
        assertFalse(failed.searching)
        assertEquals("No network", failed.error)
        assertNull(ShareSheetReducer.reduce(failed, ShareSheetIntent.ErrorCleared).error)
    }

    @Test
    fun theSearchHidesMeAndWhoIsAlreadyPicked() {
        val state = reduce(ShareSheetIntent.Open("n1"), ShareSheetIntent.Picked(ravi))
        val me = ShareSheet.candidate("u-me", "Me", "me")
        assertEquals(listOf("u-mira"), ShareSheet.visible(state, listOf(ravi, mira, me), "u-me").map { it.userId })
        assertEquals("@mira", mira.name)
        assertEquals("@ravi", ravi.handle)
    }

    @Test
    fun accessChangesAreOneEntryEach() {
        assertEquals(listOf(ShareMemberChange("u-ravi", "EDITOR")), ShareSheet.roleChange("u-ravi", NoteRole.EDITOR))
        assertEquals(listOf(ShareMemberChange("u-ravi", null)), ShareSheet.removal("u-ravi"))
        assertTrue(ShareSheet.canManage(NoteShare(shareId = "s", noteId = "n", role = "OWNER")))
        assertFalse(ShareSheet.canManage(NoteShare(shareId = "s", noteId = "n", role = "EDITOR")))
        assertEquals("Can edit", ShareSheet.label(NoteRole.EDITOR))
    }
}
