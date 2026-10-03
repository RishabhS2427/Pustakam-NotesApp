package com.app.pustakam.core.model.models.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteShareInfoTest {

    private val authors = mapOf("page-1" to "owner", "widget-9" to "ravi")

    @Test
    fun anEditorDeletesOnlyWhatTheyMadeOrHaveNotSyncedYet() {
        val editor = NoteShareInfo(shareId = "s1", role = "EDITOR", ownerId = "owner", authors = authors)
        assertTrue(editor.canWrite())
        assertFalse(editor.canShare())
        assertTrue(editor.canDelete("widget-9", "ravi"))
        assertFalse(editor.canDelete("page-1", "ravi"))
        assertTrue(editor.canDelete("added-on-this-device", "ravi"))
    }

    @Test
    fun aReaderOnlyReadsAndAnOwnerDoesEverything() {
        val reader = NoteShareInfo(shareId = "s1", role = "READER", authors = authors)
        assertFalse(reader.canWrite())
        assertFalse(reader.canDelete("widget-9", "ravi"))
        val owner = NoteShareInfo(shareId = "s1", role = "OWNER", authors = authors)
        assertTrue(owner.canWrite())
        assertTrue(owner.canShare())
        assertTrue(owner.canDelete("widget-9", "owner"))
    }

    @Test
    fun anUnknownRoleIsTreatedAsReadOnly() {
        assertEquals(NoteRole.READER, NoteShareInfo(shareId = "s1", role = "ADMIN").access)
        assertEquals(NoteRole.READER, NoteShare(shareId = "s1", noteId = "n1").access)
    }

    @Test
    fun aMemberIsNamedByNameThenHandleThenId() {
        assertEquals("Ravi", NoteShareMember(userId = "u1", name = "Ravi", username = "ravi").displayName())
        assertEquals("@ravi", NoteShareMember(userId = "u1", username = "ravi").displayName())
        assertEquals("u1", NoteShareMember(userId = "u1", name = " ").displayName())
    }
}
