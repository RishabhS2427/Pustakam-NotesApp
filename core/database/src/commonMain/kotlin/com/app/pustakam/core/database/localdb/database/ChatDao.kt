package com.app.pustakam.core.database.localdb.database

import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.database.ChatConversationEntity
import com.app.pustakam.core.database.ChatMessageEntity
import com.app.pustakam.core.database.NotesDatabase

import com.app.pustakam.core.model.models.chat.AiReplyState
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatMessageKind
import com.app.pustakam.core.model.models.chat.ChatMessageStatus
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ConversationKind
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private const val ID_SEPARATOR = ","
private const val PREVIEW_LENGTH = 140
private const val ATTACHMENT_PREVIEW = "📎 Attachment"
private const val PENDING_LIMIT = 100

// 💬 attachments live in one TEXT column as JSON. Lenient on read so a row written before a field
//   existed still decodes — the same rule the richtext adapter follows.
private val attachmentJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// Named explicitly rather than reified: encodeToString(serializer, value) is a MEMBER of
// StringFormat, so no import exists for an "optimize imports" pass to strip.
private val attachmentListSerializer = ListSerializer(ChatAttachment.serializer())

/**
 * The local thread IS what the screen renders. Everything is written here first and sent second,
 * so a bubble appears the moment it is typed and survives losing the network.
 */
class ChatDao : KoinComponent {

    private val database = get<NotesDatabase>()
    private val queries = database.notesDatabaseQueries

    // ── conversations ─────────────────────────────────────────────────────────

    fun upsertConversation(conversation: ChatConversation) {
        val peer = conversation.peer()
        queries.upsertConversation(
            id = conversation.id,
            kind = conversation.kind.name,
            participantIds = conversation.participants.joinToString(ID_SEPARATOR),
            peerId = peer?.id,
            peerName = peer?.name,
            peerAvatarUrl = peer?.avatarUrl,
            title = conversation.title,
            createdBy = conversation.createdBy,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            serverUpdatedAt = conversation.serverUpdatedAt,
            lastMessageAt = conversation.lastMessageAt,
            lastMessagePreview = conversation.lastMessagePreview,
            lastMessageSenderId = conversation.lastMessageSenderId,
            unreadCount = conversation.unreadCount.toLong(),
            deleted = if (conversation.deleted) 1L else 0L,
        )
    }

    fun upsertConversations(conversations: List<ChatConversation>) {
        database.transaction { conversations.forEach { upsertConversation(it) } }
    }

    fun conversations(): List<ChatConversation> =
        queries.selectConversations().executeAsList().map { it.toModel() }

    fun conversation(id: String): ChatConversation? =
        queries.selectConversationById(id).executeAsOneOrNull()?.toModel()

    // A one-column query gives back the scalar itself — SQLDelight only wraps multi-column rows
    fun totalUnread(): Int = queries.selectTotalUnread().executeAsOneOrNull()?.toInt() ?: 0

    fun setUnread(conversationId: String, count: Int) =
        queries.updateConversationUnread(unreadCount = count.toLong(), id = conversationId)

    fun softDeleteConversation(conversationId: String) =
        queries.softDeleteConversation(updatedAt = getCurrentTimestamp(), id = conversationId)

    // ── messages ──────────────────────────────────────────────────────────────

    fun upsertMessage(message: ChatMessage) {
        queries.upsertMessage(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            kind = message.kind.name,
            text = message.text,
            attachments = encodeAttachments(message.attachments),
            clientId = message.clientId,
            replyToId = message.replyToId,
            status = message.status.name,
            aiState = message.aiState?.name,
            aiError = message.aiError,
            createdAt = message.createdAt,
            updatedAt = message.updatedAt,
            serverUpdatedAt = message.serverUpdatedAt,
            deleted = if (message.deleted) 1L else 0L,
        )
    }

    /** One transaction for a page of history — 50 separate writes is 50 separate fsyncs. */
    fun upsertMessages(messages: List<ChatMessage>) {
        database.transaction { messages.forEach { upsertMessage(it) } }
    }

    /** Stores the message AND moves the conversation's list row, together or not at all. */
    fun upsertMessageAndTouch(message: ChatMessage) {
        database.transaction {
            upsertMessage(message)
            queries.touchConversation(
                lastMessageAt = message.createdAt,
                lastMessagePreview = previewOf(message),
                lastMessageSenderId = message.senderId,
                updatedAt = getCurrentTimestamp(),
                id = message.conversationId,
            )
        }
    }

    fun messagesPage(conversationId: String, limit: Int, page: Int): List<ChatMessage> {
        val offset = ((page - 1) * limit).coerceAtLeast(0)
        return queries
            .selectMessagesPage(conversationId = conversationId, limit = limit.toLong(), offset = offset.toLong())
            .executeAsList()
            .map { it.toModel() }
            .asReversed()
    }

    /** Older than a point, for scroll-back paging. Returned oldest-first like the page above. */
    fun messagesBefore(conversationId: String, before: Long, limit: Int): List<ChatMessage> =
        queries
            .selectMessagesBefore(conversationId = conversationId, before = before, limit = limit.toLong())
            .executeAsList()
            .map { it.toModel() }
            .asReversed()

    fun message(id: String): ChatMessage? =
        queries.selectMessageById(id).executeAsOneOrNull()?.toModel()

    fun messageByClientId(conversationId: String, clientId: String): ChatMessage? =
        queries.selectMessageByClientId(conversationId = conversationId, clientId = clientId)
            .executeAsOneOrNull()?.toModel()

    fun updateText(id: String, text: String, aiState: AiReplyState?, aiError: String?) =
        queries.updateMessageText(
            text = text,
            aiState = aiState?.name,
            aiError = aiError,
            updatedAt = getCurrentTimestamp(),
            id = id,
        )

    fun updateStatus(id: String, status: ChatMessageStatus) =
        queries.updateMessageStatus(status = status.name, updatedAt = getCurrentTimestamp(), id = id)

    /**
     * Swaps the optimistic local id for the server's. INSERT OR REPLACE cannot do this — a new
     * primary key is a new row, which is how a sent message ends up on screen twice.
     */
    fun adoptServerId(localId: String, serverId: String, status: ChatMessageStatus, serverUpdatedAt: Long?) {
        if (localId == serverId) {
            updateStatus(localId, status)
            return
        }
        database.transaction {
            queries.replaceMessageId(
                serverId = serverId,
                status = status.name,
                serverUpdatedAt = serverUpdatedAt,
                updatedAt = getCurrentTimestamp(),
                localId = localId,
            )
        }
    }

    fun markOutgoingRead(conversationId: String, senderId: String, upTo: Long) =
        queries.markOutgoingRead(
            updatedAt = getCurrentTimestamp(),
            conversationId = conversationId,
            senderId = senderId,
            upTo = upTo,
        )

    /** The outbox. Everything typed offline, oldest first so the thread reads in the right order. */
    fun pendingMessages(limit: Int = PENDING_LIMIT): List<ChatMessage> =
        queries.selectPendingMessages(limit.toLong()).executeAsList().map { it.toModel() }

    fun softDeleteMessage(id: String) =
        queries.softDeleteMessage(updatedAt = getCurrentTimestamp(), id = id)

    fun deleteMessagesFor(conversationId: String) = queries.deleteMessagesForConversation(conversationId)

    // ── mapping ───────────────────────────────────────────────────────────────

    private fun ChatConversationEntity.toModel(): ChatConversation = ChatConversation(
        id = id,
        kind = kind.toConversationKind(),
        participants = participantIds.split(ID_SEPARATOR).filter { it.isNotBlank() },
        participantsInfo = peerId?.let {
            listOf(ChatParticipant(id = it, name = peerName, avatarUrl = peerAvatarUrl))
        } ?: emptyList(),
        title = title,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        serverUpdatedAt = serverUpdatedAt,
        lastMessageAt = lastMessageAt,
        lastMessagePreview = lastMessagePreview,
        lastMessageSenderId = lastMessageSenderId,
        unreadCount = unreadCount.toInt(),
        deleted = deleted == 1L,
    )

    private fun ChatMessageEntity.toModel(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        kind = kind.toMessageKind(),
        text = text,
        attachments = decodeAttachments(attachments),
        clientId = clientId,
        replyToId = replyToId,
        status = status.toMessageStatus(),
        aiState = aiState?.toAiState(),
        aiError = aiError,
        createdAt = createdAt,
        updatedAt = updatedAt,
        serverUpdatedAt = serverUpdatedAt,
        deleted = deleted == 1L,
    )

    private fun encodeAttachments(attachments: List<ChatAttachment>): String =
        if (attachments.isEmpty()) "" else attachmentJson.encodeToString(attachmentListSerializer, attachments)

    // A row this device cannot read must not take the whole thread down with it
    private fun decodeAttachments(raw: String?): List<ChatAttachment> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            attachmentJson.decodeFromString(attachmentListSerializer, raw)
        } catch (e: Throwable) {
            log_d("ChatDao", "unreadable attachments column: $e")
            emptyList()
        }
    }

    private fun previewOf(message: ChatMessage): String {
        val text = message.text.trim()
        if (text.isNotEmpty()) return text.take(PREVIEW_LENGTH)
        return if (message.attachments.isNotEmpty()) ATTACHMENT_PREVIEW else ""
    }
}

// An unknown enum name from a newer build must degrade, never crash an older one
private fun String.toConversationKind(): ConversationKind =
    ConversationKind.entries.firstOrNull { it.name == this } ?: ConversationKind.DIRECT

private fun String.toMessageKind(): ChatMessageKind =
    ChatMessageKind.entries.firstOrNull { it.name == this } ?: ChatMessageKind.USER

private fun String.toMessageStatus(): ChatMessageStatus =
    ChatMessageStatus.entries.firstOrNull { it.name == this } ?: ChatMessageStatus.SENT

private fun String.toAiState(): AiReplyState? = AiReplyState.entries.firstOrNull { it.name == this }
