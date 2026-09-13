package com.app.pustakam.feature.chat.data.wire

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.chat.AiReplyState
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatAttachmentWire
import com.app.pustakam.core.model.models.chat.ChatConversationWire
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatMessageKind
import com.app.pustakam.core.model.models.chat.ChatMessageStatus
import com.app.pustakam.core.model.models.chat.ChatMessageWire
import com.app.pustakam.core.model.models.chat.ChatParticipantWire
import com.app.pustakam.core.model.models.chat.ConversationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatWireMapperTest {

    @Test
    fun `timestamp strings become millis, and unreadable ones become zero rather than throwing`() {
        val wire = ChatMessageWire(
            id = "m1", conversationId = "c1", createdAt = "1700000000000", updatedAt = "not a number",
        )

        val message = ChatWireMapper.toDomain(wire)

        assertEquals(1_700_000_000_000L, message.createdAt)
        assertEquals(0L, message.updatedAt, "one bad row must not take the whole thread down")
    }

    @Test
    fun `localPath never leaves the device`() {
        val attachment = ChatAttachment(
            assetId = "a1", url = "https://cdn/x.png", contentType = ContentType.IMAGE,
            localPath = "/data/user/0/x.png",
        )

        val encoded = ChatWireMapper.toWire(attachment)

        assertEquals("a1", encoded.assetId)
        assertEquals("IMAGE", encoded.contentType)
        // ChatAttachmentWire has no localPath field at all — that is the point of the separate type
        assertTrue(encoded.url.isNotBlank())
    }

    @Test
    fun `a pulled message keeps the local file this device already downloaded`() {
        val existing = ChatMessage(
            id = "m1", conversationId = "c1",
            attachments = listOf(ChatAttachment(assetId = "a1", localPath = "/data/x.png")),
        )
        val pulled = ChatWireMapper.toDomain(
            ChatMessageWire(
                id = "m1", conversationId = "c1",
                attachments = listOf(ChatAttachmentWire(assetId = "a1", url = "https://cdn/x.png")),
            )
        )

        val merged = ChatWireMapper.restoreLocalPaths(pulled, existing)

        assertEquals("/data/x.png", merged.attachments.first().localPath, "a pull must not orphan files on disk")
        assertEquals("https://cdn/x.png", merged.attachments.first().url)
    }

    @Test
    fun `a pull with nothing local to restore is left alone`() {
        val pulled = ChatWireMapper.toDomain(ChatMessageWire(id = "m1", conversationId = "c1"))
        assertEquals(pulled, ChatWireMapper.restoreLocalPaths(pulled, null))
    }

    @Test
    fun `an enum name a newer server invented degrades instead of crashing`() {
        val message = ChatWireMapper.toDomain(
            ChatMessageWire(id = "m1", conversationId = "c1", kind = "HOLOGRAM", status = "TELEPORTED")
        )

        assertEquals(ChatMessageKind.USER, message.kind)
        assertEquals(ChatMessageStatus.SENT, message.status)
        assertNull(message.aiState)
    }

    @Test
    fun `ai state round-trips`() {
        val message = ChatWireMapper.toDomain(
            ChatMessageWire(id = "m1", conversationId = "c1", kind = "AI", aiState = "STREAMING")
        )
        assertEquals(ChatMessageKind.AI, message.kind)
        assertEquals(AiReplyState.STREAMING, message.aiState)
    }

    @Test
    fun `a conversation carries its peer and its unread count`() {
        val conversation = ChatWireMapper.toDomain(
            ChatConversationWire(
                id = "c1", kind = "DIRECT", participants = listOf("u1", "u2"),
                lastMessageAt = "1700000000000", unreadCount = 3,
            )
        )

        assertEquals(ConversationKind.DIRECT, conversation.kind)
        assertEquals(3, conversation.unreadCount)
        assertEquals(1_700_000_000_000L, conversation.lastMessageAt)
    }

    @Test
    fun `a send request carries the clientId that makes a retry safe`() {
        val message = ChatMessage(id = "local-1", conversationId = "c1", text = "hi", clientId = "local-1")

        assertEquals("local-1", ChatWireMapper.toSendRequest(message).clientId)
        assertEquals("local-1", ChatWireMapper.toSocketSend(message).clientId)
        assertEquals("c1", ChatWireMapper.toSocketSend(message).conversationId)
    }

    @Test
    fun `a participant maps to the handle, and carries no contact details`() {
        val participant = ChatWireMapper.toDomain(
            ChatParticipantWire(id = "u1", username = "rishabh", name = null, avatarUrl = null)
        )

        assertEquals("rishabh", participant.username)
        assertEquals("@rishabh", participant.displayName())
    }

    @Test
    fun `an OLD server that still sends email and phone decodes without throwing`() {
        // ignoreUnknownKeys plus nullable-with-default is what lets the server ship before the app
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            ChatParticipantWire.serializer(),
            """{"_id":"u1","name":"R","email":"r@example.com","phone":"+91","avatarUrl":null}"""
        )

        assertEquals("u1", decoded.id)
        assertNull(decoded.username)
    }
}
