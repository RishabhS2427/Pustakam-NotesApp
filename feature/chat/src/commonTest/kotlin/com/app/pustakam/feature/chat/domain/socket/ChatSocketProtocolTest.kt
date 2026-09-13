package com.app.pustakam.feature.chat.domain.socket

import com.app.pustakam.core.model.models.chat.SocketReadPayload
import com.app.pustakam.core.model.models.chat.SocketSendPayload
import com.app.pustakam.core.model.models.chat.SocketTypingPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 💬 The other half of this contract is PustakmServer/realtime/wsProtocol.js. If a change here
 * makes one of these fail, the server almost certainly needs the same change.
 */
class ChatSocketProtocolTest {

    @Test
    fun `a send frame carries its correlation id`() {
        val frame = ChatSocketProtocol.encodeSend(
            frameId = "f1",
            payload = SocketSendPayload(conversationId = "c1", text = "hello", clientId = "c-1"),
        )

        assertTrue(frame.contains("\"t\":\"send\""))
        assertTrue(frame.contains("\"id\":\"f1\""), "without the id the ack cannot find its bubble")
        assertTrue(frame.contains("\"conversationId\":\"c1\""))
        assertTrue(frame.contains("hello"))
    }

    @Test
    fun `typing and read frames encode`() {
        assertTrue(
            ChatSocketProtocol.encodeTyping(SocketTypingPayload("c1", isTyping = true))
                .contains("\"t\":\"typing\"")
        )
        assertTrue(
            ChatSocketProtocol.encodeRead("f2", SocketReadPayload("c1", lastReadAt = 42))
                .contains("\"t\":\"read\"")
        )
    }

    @Test
    fun `an incoming message decodes into an event`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"message","d":{"_id":"m1","conversationId":"c1","senderId":"u1","kind":"USER",
               "text":"hi","status":"SENT","createdAt":"1700000000000","serverUpdatedAt":1700000000001}}"""
        )

        val message = assertIs<ChatSocketEvent.MessageReceived>(event).message
        assertEquals("m1", message.id)
        assertEquals("hi", message.text)
        assertEquals("1700000000000", message.createdAt)
    }

    @Test
    fun `an ack keeps the frame id so the optimistic row can be settled`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"ack","id":"f1","d":{"clientId":"c-1","duplicate":false,
               "message":{"_id":"m9","conversationId":"c1","kind":"USER","status":"SENT"}}}"""
        )

        val ack = assertIs<ChatSocketEvent.Ack>(event)
        assertEquals("f1", ack.frameId)
        assertEquals("c-1", ack.clientId)
        assertEquals("m9", ack.message?.id)
    }

    @Test
    fun `an ack answering a read frame decodes as a receipt, not an empty ack`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"ack","id":"f3","d":{"conversationId":"c1","userId":"u2","lastReadAt":99,"changed":2}}"""
        )

        val read = assertIs<ChatSocketEvent.Read>(event)
        assertEquals("c1", read.receipt.conversationId)
        assertEquals(99L, read.receipt.lastReadAt)
    }

    @Test
    fun `an ai delta carries the message it belongs to`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"ai_delta","d":{"conversationId":"c1","messageId":"m2","delta":"Hel"}}"""
        )

        val delta = assertIs<ChatSocketEvent.AiDelta>(event)
        assertEquals("m2", delta.messageId)
        assertEquals("Hel", delta.delta)
    }

    @Test
    fun `typing decodes with the sender`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"typing","d":{"conversationId":"c1","userId":"u2","isTyping":true}}"""
        )

        val typing = assertIs<ChatSocketEvent.Typing>(event)
        assertEquals("u2", typing.userId)
        assertTrue(typing.isTyping)
    }

    @Test
    fun `an error frame becomes a failure the sender can act on`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"error","id":"f7","d":{"code":"MESSAGE_TOO_LONG","message":"too long"}}"""
        )

        val failure = assertIs<ChatSocketEvent.Failure>(event)
        assertEquals("f7", failure.frameId)
        assertEquals("MESSAGE_TOO_LONG", failure.code)
    }

    @Test
    fun `junk never throws - one bad frame must not kill the connection`() {
        assertIs<ChatSocketEvent.Failure>(ChatSocketProtocol.decode("this is not json"))
        assertIs<ChatSocketEvent.Failure>(ChatSocketProtocol.decode("{}"))
        assertIs<ChatSocketEvent.Failure>(ChatSocketProtocol.decode("""{"t":"message"}"""))
    }

    @Test
    fun `a frame type this build does not know is reported, not fatal`() {
        val event = ChatSocketProtocol.decode("""{"t":"quantum_entangle","d":{}}""")
        assertEquals("quantum_entangle", assertIs<ChatSocketEvent.Unsupported>(event).type)
    }

    @Test
    fun `a pong decodes with no payload at all`() {
        assertIs<ChatSocketEvent.Pong>(ChatSocketProtocol.decode("""{"t":"pong","d":{}}"""))
    }

    @Test
    fun `an unknown field from a newer server is ignored rather than fatal`() {
        val event = ChatSocketProtocol.decode(
            """{"t":"message","unknownTop":1,"d":{"_id":"m1","conversationId":"c1","kind":"USER",
               "status":"SENT","somethingNew":true}}"""
        )
        assertIs<ChatSocketEvent.MessageReceived>(event)
    }

    @Test
    fun `the token goes in the query only when there is one`() {
        assertEquals("wss://h/ws/chat?token=abc", socketUrlWithToken("wss://h/ws/chat", "abc"))
        assertEquals("wss://h/ws/chat?x=1&token=abc", socketUrlWithToken("wss://h/ws/chat?x=1", "abc"))
        assertEquals("wss://h/ws/chat", socketUrlWithToken("wss://h/ws/chat", ""))
    }
}
