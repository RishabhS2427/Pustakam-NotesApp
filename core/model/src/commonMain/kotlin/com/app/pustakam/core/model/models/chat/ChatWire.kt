package com.app.pustakam.core.model.models.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 💬 The WIRE shapes, matching PustakmServer/domain/chatMapper.js field for field.
//   Timestamps arrive as strings and serverUpdatedAt as a number — the same split the note wire
//   uses. ChatWireMapper in :feature:chat is the only code that converts between these and the
//   domain models; nothing else should ever see a ChatMessageWire.

@Serializable
data class ChatAttachmentWire(
    val assetId: String? = null,
    val url: String = "",
    val contentType: String = "OTHER",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0,
    val title: String = "",
    val checksum: String? = null,
)

@Serializable
data class ChatParticipantWire(
    @SerialName("_id")
    val id: String,
    // 🆔 31-Aug-2026 — the server's public view: username in, email and phone gone. Every field
    //   is nullable-with-default and the Json is ignoreUnknownKeys, so an OLD server that still
    //   sends email/phone decodes fine — which is what lets the server ship first.
    val username: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
)

@Serializable
data class ChatMessageWire(
    @SerialName("_id")
    val id: String,
    val conversationId: String,
    val senderId: String? = null,
    val kind: String = "USER",
    val text: String = "",
    val attachments: List<ChatAttachmentWire> = emptyList(),
    val clientId: String? = null,
    val replyToId: String? = null,
    val status: String = "SENT",
    val aiState: String? = null,
    val aiError: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val serverUpdatedAt: Long? = null,
    val deleted: Boolean = false,
)

@Serializable
data class ChatConversationWire(
    @SerialName("_id")
    val id: String,
    val kind: String = "DIRECT",
    val participants: List<String> = emptyList(),
    val participantsInfo: List<ChatParticipantWire> = emptyList(),
    val title: String? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val serverUpdatedAt: Long? = null,
    val lastMessageAt: String? = null,
    val lastMessagePreview: String = "",
    val lastMessageSenderId: String? = null,
    val unreadCount: Int = 0,
    val deleted: Boolean = false,
)

// ── request bodies ────────────────────────────────────────────────────────────

@Serializable
data class OpenConversationRequest(
    val kind: String,
    val participantId: String? = null,
    val title: String? = null,
)

@Serializable
data class SendMessageRequest(
    val text: String = "",
    val attachments: List<ChatAttachmentWire> = emptyList(),
    val clientId: String? = null,
    val replyToId: String? = null,
)

@Serializable
data class MarkReadRequest(
    val lastReadAt: Long,
    val lastReadMessageId: String? = null,
)

@Serializable
data class ReadReceiptWire(
    val conversationId: String,
    val userId: String,
    val lastReadAt: Long = 0,
    val lastReadMessageId: String? = null,
    val changed: Int = 0,
)

@Serializable
data class DeleteConversationResponse(
    val id: String,
    val deleted: Boolean = false,
)

// ── websocket payloads ────────────────────────────────────────────────────────
// The envelope itself is { t, id?, d }; these are the `d` bodies. PustakmServer/realtime/
// wsProtocol.js is the other half of this contract — change one and you must change both.

@Serializable
data class SocketReadyPayload(
    val userId: String,
    val serverTime: Long = 0,
    val aiParticipantId: String = AI_PARTICIPANT_ID,
)

@Serializable
data class SocketSendPayload(
    val conversationId: String,
    val text: String = "",
    val attachments: List<ChatAttachmentWire> = emptyList(),
    val clientId: String? = null,
    val replyToId: String? = null,
)

@Serializable
data class SocketAckPayload(
    val clientId: String? = null,
    val message: ChatMessageWire? = null,
    val duplicate: Boolean = false,
)

@Serializable
data class SocketTypingPayload(
    val conversationId: String,
    val userId: String = "",
    val isTyping: Boolean = false,
)

@Serializable
data class SocketReadPayload(
    val conversationId: String,
    val lastReadAt: Long = 0,
    val lastReadMessageId: String? = null,
)

@Serializable
data class SocketAiDeltaPayload(
    val conversationId: String,
    val messageId: String,
    val delta: String = "",
)

@Serializable
data class SocketAiDonePayload(
    val conversationId: String,
    val message: ChatMessageWire? = null,
)

@Serializable
data class SocketErrorPayload(
    val code: String = "UNKNOWN",
    val message: String = "",
)
