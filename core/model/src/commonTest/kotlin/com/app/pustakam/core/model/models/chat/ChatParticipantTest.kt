package com.app.pustakam.core.model.models.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🆔 The regression that removing email and phone creates. displayName() used to fall back to the
 * email address and then the phone number; without the handle in their place, every un-named peer
 * would silently render as "Someone".
 */
class ChatParticipantTest {

    @Test
    fun `a name wins when there is one`() {
        val participant = ChatParticipant(id = "u1", username = "rishabh", name = "Rishabh S")
        assertEquals("Rishabh S", participant.displayName())
    }

    @Test
    fun `with no name it falls back to the handle, not to Someone`() {
        val participant = ChatParticipant(id = "u1", username = "rishabh")
        assertEquals("@rishabh", participant.displayName())
    }

    @Test
    fun `a blank name is treated as no name`() {
        val participant = ChatParticipant(id = "u1", username = "rishabh", name = "   ")
        assertEquals("@rishabh", participant.displayName())
    }

    @Test
    fun `only with neither does it fall through`() {
        assertEquals(UNKNOWN_PARTICIPANT, ChatParticipant(id = "u1").displayName())
    }

    @Test
    fun `handle is empty rather than a bare at-sign when there is no username`() {
        assertEquals("", ChatParticipant(id = "u1", name = "Rishabh S").handle())
        assertEquals("@rishabh", ChatParticipant(id = "u1", username = "rishabh").handle())
    }

    @Test
    fun `the avatar initial never comes out blank`() {
        assertEquals("R", ChatParticipant(id = "u1", name = "Rishabh S").initial())
        assertEquals("R", ChatParticipant(id = "u1", username = "rishabh").initial())
        assertTrue(ChatParticipant(id = "u1").initial().isNotEmpty())
    }

    @Test
    fun `the assistant still reads correctly with no username at all`() {
        val assistant = ChatParticipant(id = AI_PARTICIPANT_ID, name = AI_DISPLAY_NAME)
        assertEquals(AI_DISPLAY_NAME, assistant.displayName())
        assertEquals("", assistant.handle())
    }
}
