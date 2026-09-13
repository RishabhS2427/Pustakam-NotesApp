package com.app.pustakam.feature.chat.data.wire

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.chat.AiReplyState
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatAttachmentWire
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatConversationWire
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatMessageKind
import com.app.pustakam.core.model.models.chat.ChatMessageStatus
import com.app.pustakam.core.model.models.chat.ChatMessageWire
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ChatParticipantWire
import com.app.pustakam.core.model.models.chat.ConversationKind
import com.app.pustakam.core.model.models.chat.ReadReceipt
import com.app.pustakam.core.model.models.chat.ReadReceiptWire
import com.app.pustakam.core.model.models.chat.SendMessageRequest
import com.app.pustakam.core.model.models.chat.SocketSendPayload

/**
 * 💬 The ONLY place that knows the server speaks in timestamp strings while the app works in Longs,
 * and the only place that strips device state off an outgoing attachment.
 *
 * Two rules, both learned from the note sync:
 *   • `localPath` never leaves the device. It is this phone's copy of the bytes; sending it makes
 *     the other phone hold a path into a container it does not have.
 *   • an unparseable timestamp becomes 0, never an exception. One malformed row must not take the
 *     whole thread down with it.
 */
object ChatWireMapper {

    fun toDomain(wire: ChatMessageWire): ChatMessage = ChatMessage(
        id = wire.id,
        conversationId = wire.conversationId,
        senderId = wire.senderId,
        kind = wire.kind.toMessageKind(),
        text = wire.text,
        attachments = wire.attachments.map { toDomain(it) },
        clientId = wire.clientId,
        replyToId = wire.replyToId,
        status = wire.status.toMessageStatus(),
        aiState = wire.aiState?.toAiState(),
        aiError = wire.aiError,
        createdAt = wire.createdAt.toEpochMillis(),
        updatedAt = wire.updatedAt.toEpochMillis(),
        serverUpdatedAt = wire.serverUpdatedAt,
        deleted = wire.deleted,
    )

    fun toDomain(wire: ChatConversationWire): ChatConversation = ChatConversation(
        id = wire.id,
        kind = wire.kind.toConversationKind(),
        participants = wire.participants,
        participantsInfo = wire.participantsInfo.map { toDomain(it) },
        title = wire.title,
        createdBy = wire.createdBy,
        createdAt = wire.createdAt.toEpochMillis(),
        updatedAt = wire.updatedAt.toEpochMillis(),
        serverUpdatedAt = wire.serverUpdatedAt,
        lastMessageAt = wire.lastMessageAt.toEpochMillis(),
        lastMessagePreview = wire.lastMessagePreview,
        lastMessageSenderId = wire.lastMessageSenderId,
        unreadCount = wire.unreadCount,
        deleted = wire.deleted,
    )

    fun toDomain(wire: ChatParticipantWire): ChatParticipant = ChatParticipant(
        id = wire.id,
        // 🆔 31-Aug-2026 — the server's public view. email/phone are gone from both shapes; the
        //   username is what identifies a person to anyone who is not them.
        username = wire.username,
        name = wire.name,
        avatarUrl = wire.avatarUrl,
        bio = wire.bio,
    )

    fun toDomain(wire: ChatAttachmentWire): ChatAttachment = ChatAttachment(
        assetId = wire.assetId,
        url = wire.url,
        contentType = wire.contentType.toContentType(),
        mimeType = wire.mimeType,
        sizeBytes = wire.sizeBytes,
        width = wire.width,
        height = wire.height,
        duration = wire.duration,
        title = wire.title,
        checksum = wire.checksum,
        // localPath is restored from the local row by the repository, never taken from the wire
        localPath = null,
    )

    fun toDomain(wire: ReadReceiptWire): ReadReceipt = ReadReceipt(
        conversationId = wire.conversationId,
        userId = wire.userId,
        lastReadAt = wire.lastReadAt,
        lastReadMessageId = wire.lastReadMessageId,
    )

    /** 🖼️ localPath is dropped here. This is the seam that keeps device paths off the network. */
    fun toWire(attachment: ChatAttachment): ChatAttachmentWire = ChatAttachmentWire(
        assetId = attachment.assetId,
        url = attachment.url,
        contentType = attachment.contentType.name,
        mimeType = attachment.mimeType,
        sizeBytes = attachment.sizeBytes,
        width = attachment.width,
        height = attachment.height,
        duration = attachment.duration,
        title = attachment.title,
        checksum = attachment.checksum,
    )

    fun toSendRequest(message: ChatMessage): SendMessageRequest = SendMessageRequest(
        text = message.text,
        attachments = message.attachments.map { toWire(it) },
        clientId = message.clientId,
        replyToId = message.replyToId,
    )

    fun toSocketSend(message: ChatMessage): SocketSendPayload = SocketSendPayload(
        conversationId = message.conversationId,
        text = message.text,
        attachments = message.attachments.map { toWire(it) },
        clientId = message.clientId,
        replyToId = message.replyToId,
    )

    /**
     * A pulled message must not orphan bytes already on this disk. The server does not know about
     * localPath, so it is carried over from the row this device already has — matched on assetId.
     */
    fun restoreLocalPaths(incoming: ChatMessage, existing: ChatMessage?): ChatMessage {
        if (existing == null || existing.attachments.isEmpty()) return incoming
        val knownPaths = existing.attachments
            .filter { !it.assetId.isNullOrBlank() && !it.localPath.isNullOrBlank() }
            .associate { it.assetId to it.localPath }
        if (knownPaths.isEmpty()) return incoming

        return incoming.copy(
            attachments = incoming.attachments.map { attachment ->
                knownPaths[attachment.assetId]?.let { attachment.withLocalPath(it) } ?: attachment
            }
        )
    }
}

// A timestamp the server sent as "1712345678901". Anything unreadable becomes 0 rather than throwing.
private fun String?.toEpochMillis(): Long = this?.trim()?.toLongOrNull() ?: 0L

// An enum name from a newer server must degrade to something usable, never crash an older client
private fun String.toConversationKind(): ConversationKind =
    ConversationKind.entries.firstOrNull { it.name == this } ?: ConversationKind.DIRECT

private fun String.toMessageKind(): ChatMessageKind =
    ChatMessageKind.entries.firstOrNull { it.name == this } ?: ChatMessageKind.USER

private fun String.toMessageStatus(): ChatMessageStatus =
    ChatMessageStatus.entries.firstOrNull { it.name == this } ?: ChatMessageStatus.SENT

private fun String.toAiState(): AiReplyState? = AiReplyState.entries.firstOrNull { it.name == this }

private fun String.toContentType(): ContentType =
    ContentType.entries.firstOrNull { it.name == this } ?: ContentType.OTHER
