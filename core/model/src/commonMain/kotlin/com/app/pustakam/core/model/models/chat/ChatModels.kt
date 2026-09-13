package com.app.pustakam.core.model.models.chat

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 💬 The DOMAIN models. Timestamps are Long here because ordering a thread is the whole job.
//   The server speaks in strings; ChatWireMapper in :feature:chat is the only place that knows.

@Serializable
enum class ConversationKind { DIRECT, AI }

@Serializable
enum class ChatMessageKind { USER, AI, SYSTEM }

// SENDING exists only on this device — the server never sees it and never sends it back
@Serializable
enum class ChatMessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

@Serializable
enum class AiReplyState { THINKING, STREAMING, DONE, FAILED }

@Serializable
data class ChatParticipant(
    @SerialName("_id")
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
) {
    // 🆔 31-Aug-2026 — the fallback USED to be email then phone. Dropping those without
    //   changing this would turn every un-named peer into "Someone"; the handle takes their place.
    fun displayName(): String = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: UNKNOWN_PARTICIPANT

    fun handle(): String = username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""

    fun initial(): String = displayName().trim().firstOrNull()?.uppercase() ?: "?"
}

const val UNKNOWN_PARTICIPANT = "Someone"
const val AI_PARTICIPANT_ID = "ai-assistant"
const val AI_DISPLAY_NAME = "Pustakam AI"

@Serializable
data class ChatAttachment(
    val assetId: String? = null,
    val url: String = "",
    val contentType: ContentType = ContentType.OTHER,
    val mimeType: String = "",
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0,
    val title: String = "",
    val checksum: String? = null,
    // 🖼️ device state, exactly like NoteContent.localPath. ChatWireMapper strips it before a send,
    //   or the other phone receives a path into a container it does not have.
    val localPath: String? = null,
) {
    fun withLocalPath(path: String?): ChatAttachment = copy(localPath = path)
    fun withAsset(assetId: String?, url: String, checksum: String?): ChatAttachment =
        copy(assetId = assetId, url = url, checksum = checksum)

    fun needsUpload(): Boolean = assetId.isNullOrBlank() && !localPath.isNullOrBlank()
    fun needsDownload(): Boolean = !assetId.isNullOrBlank() && localPath.isNullOrBlank()
}

/**
 * 🧩 DRY: chat does NOT get its own image, video, audio or document card. An attachment becomes a
 * MediaContent and the note widgets render it — the same ImageCard/VideoCard/AudioPlayer/
 * DocumentFileCard on Android and the same block views on iOS.
 */
fun ChatAttachment.toMediaContent(conversationId: String, id: String, index: Int): NoteContentModel.MediaContent =
    NoteContentModel.MediaContent(
        position = index.toDouble(),
        noteId = conversationId,
        type = contentType,
        updatedAt = null,
        createdAt = null,
        id = id,
        duration = duration,
        localPath = localPath,
        url = url,
        title = title,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        assetId = assetId,
        checksum = checksum,
    )

@Serializable
data class ChatMessage(
    @SerialName("_id")
    val id: String,
    val conversationId: String,
    val senderId: String? = null,
    val kind: ChatMessageKind = ChatMessageKind.USER,
    val text: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    // 🔁 the device's own id for this send. A retry repeats it and the server answers with the
    //   first copy, so a dropped ack can never post the message twice.
    val clientId: String? = null,
    val replyToId: String? = null,
    val status: ChatMessageStatus = ChatMessageStatus.SENDING,
    val aiState: AiReplyState? = null,
    val aiError: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val serverUpdatedAt: Long? = null,
    val deleted: Boolean = false,
) {
    // Swift cannot use Kotlin's copy() defaults, so every edit gets a named helper (Note does the same)
    fun withStatus(next: ChatMessageStatus): ChatMessage =
        copy(status = next, updatedAt = getCurrentTimestamp())

    fun withText(next: String): ChatMessage = copy(text = next, updatedAt = getCurrentTimestamp())

    /** 🤖 a streaming reply grows in place rather than arriving whole. */
    fun appendingDelta(delta: String): ChatMessage =
        copy(text = text + delta, aiState = AiReplyState.STREAMING, updatedAt = getCurrentTimestamp())

    fun withAiState(state: AiReplyState?, error: String? = null): ChatMessage =
        copy(aiState = state, aiError = error, updatedAt = getCurrentTimestamp())

    fun withAttachments(next: List<ChatAttachment>): ChatMessage =
        copy(attachments = next, updatedAt = getCurrentTimestamp())

    fun isMine(userId: String?): Boolean = senderId != null && senderId == userId
    fun isFromAssistant(): Boolean = kind == ChatMessageKind.AI
    fun isStreaming(): Boolean = aiState == AiReplyState.THINKING || aiState == AiReplyState.STREAMING
    fun hasBody(): Boolean = text.isNotBlank() || attachments.isNotEmpty()

    fun mediaContents(): List<NoteContentModel.MediaContent> =
        attachments.mapIndexed { index, attachment ->
            attachment.toMediaContent(conversationId, "$id-$index", index)
        }
}

@Serializable
data class ChatConversation(
    @SerialName("_id")
    val id: String,
    val kind: ConversationKind = ConversationKind.DIRECT,
    val participants: List<String> = emptyList(),
    val participantsInfo: List<ChatParticipant> = emptyList(),
    val title: String? = null,
    val createdBy: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val serverUpdatedAt: Long? = null,
    val lastMessageAt: Long = 0,
    val lastMessagePreview: String = "",
    val lastMessageSenderId: String? = null,
    val unreadCount: Int = 0,
    val deleted: Boolean = false,
) {
    fun peer(): ChatParticipant? = participantsInfo.firstOrNull()

    fun displayTitle(): String = when {
        !title.isNullOrBlank() -> title
        kind == ConversationKind.AI -> AI_DISPLAY_NAME
        else -> peer()?.displayName() ?: UNKNOWN_PARTICIPANT
    }

    fun avatarUrl(): String? = peer()?.avatarUrl

    fun withUnread(count: Int): ChatConversation = copy(unreadCount = count)

    fun isAssistant(): Boolean = kind == ConversationKind.AI
}

/** 🔌 What the socket is doing, so the screen can show "connecting…" honestly. */
@Serializable
enum class ChatConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

@Serializable
data class TypingSignal(
    val conversationId: String,
    val userId: String,
    val isTyping: Boolean,
)

@Serializable
data class ReadReceipt(
    val conversationId: String,
    val userId: String,
    val lastReadAt: Long,
    val lastReadMessageId: String? = null,
)

const val CHAT_PAGE_SIZE = 50
