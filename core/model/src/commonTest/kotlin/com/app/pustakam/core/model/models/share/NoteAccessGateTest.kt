package com.app.pustakam.core.model.models.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteAccessGateTest {

    private val authors = mapOf("page-1" to "owner", "text-2" to "ravi")

    private fun gate(role: String, userId: String) =
        NoteAccessGate(NoteShareInfo(shareId = "s1", role = role, ownerId = "owner", ownerName = "Asha", authors = authors), userId)

    @Test
    fun myOwnNoteIsWideOpen() {
        val mine = NoteAccessGate.open("me")
        assertFalse(mine.shared)
        assertFalse(mine.readOnly)
        assertTrue(mine.canDeleteNote)
        assertTrue(mine.canShare)
        assertTrue(mine.canDelete("anything"))
        assertNull(mine.sharedBy)
        assertNull(mine.label)
    }

    @Test
    fun aReaderOfASharedCopyOnlyLooks() {
        val reader = gate("READER", "mira")
        assertTrue(reader.readOnly)
        assertFalse(reader.canDeleteNote)
        assertFalse(reader.canShare)
        assertFalse(reader.canDelete("text-2"))
        assertEquals("Asha", reader.sharedBy)
        assertEquals("View only · Shared by Asha", reader.label)
        assertEquals(NoteAccessGate.READ_ONLY_DENIED, reader.deleteDenial)
    }

    @Test
    fun anEditorRemovesOnlyTheirOwnWork() {
        val editor = gate("EDITOR", "ravi")
        assertFalse(editor.readOnly)
        assertTrue(editor.canDelete("text-2"))
        assertFalse(editor.canDelete("page-1"))
        assertFalse(editor.canDeleteAll(listOf("text-2", "page-1")))
        assertTrue(editor.canDeleteAll(listOf("text-2")))
        assertFalse(editor.canDeleteNote)
        assertEquals("Shared by Asha", editor.label)
        assertEquals(NoteAccessGate.DELETE_DENIED, editor.deleteDenial)
    }

    @Test
    fun anOwnerWithoutANameIsStillNamedPolitely() {
        val nameless = NoteAccessGate(NoteShareInfo(shareId = "s1", role = "EDITOR", ownerId = "owner"), "ravi")
        assertEquals("Shared with you", nameless.label)
    }

    @Test
    fun theOwnerOfTheSharedCopyKeepsEveryRight() {
        val owner = gate("OWNER", "owner")
        assertTrue(owner.canDeleteNote)
        assertTrue(owner.canShare)
        assertTrue(owner.canDelete("text-2"))
        assertNull(owner.sharedBy)
        assertEquals("Shared by you", owner.label)
    }
}
