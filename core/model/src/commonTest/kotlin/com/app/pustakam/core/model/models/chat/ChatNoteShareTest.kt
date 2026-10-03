package com.app.pustakam.core.model.models.chat

import com.app.pustakam.core.common.util.ContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatNoteShareTest {

    private val card = ChatAttachment(contentType = ContentType.LINK, url = "pustakam://note/copy-1", title = "Trip")
    private val photo = ChatAttachment(assetId = "a1", contentType = ContentType.IMAGE)

    @Test
    fun aSharedNoteCardNamesTheCopyItOpens() {
        assertEquals("copy-1", card.sharedNoteId())
        assertNull(photo.sharedNoteId())
        assertNull(ChatAttachment(contentType = ContentType.LINK, url = "https://example.com").sharedNoteId())
        assertNull(ChatAttachment(contentType = ContentType.OTHER, url = "pustakam://note/copy-1").sharedNoteId())
        assertNull(ChatAttachment(contentType = ContentType.LINK, url = "pustakam://note/").sharedNoteId())
    }

    @Test
    fun aCardIsNotMediaAndMediaKeepsItsIds() {
        val message = ChatMessage(id = "m1", conversationId = "c1", attachments = listOf(card, photo))
        assertEquals(listOf(card), message.sharedNotes())
        assertEquals(listOf("m1-1"), message.mediaContents().map { it.id })
    }
}
