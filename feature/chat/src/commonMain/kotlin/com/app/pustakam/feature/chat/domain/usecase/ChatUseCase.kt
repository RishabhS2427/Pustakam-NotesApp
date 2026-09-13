package com.app.pustakam.feature.chat.domain.usecase

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.data.usecases.BaseUseCase
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ConversationKind
import com.app.pustakam.feature.chat.domain.repository.IChatRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.inject

/**
 * 💬 One class, one action — the same shape the notes use cases follow. Everything reads through
 * the interface; nothing here knows there is a websocket underneath.
 */
abstract class ChatBaseUseCase : BaseUseCase() {
    protected val chatRepository: IChatRepository by inject()

    val conversations = chatRepository.conversationsState
    val messages = chatRepository.activeMessagesState
    val connection = chatRepository.connectionState
    val typing = chatRepository.typingState
    val totalUnread = chatRepository.totalUnreadState
    val currentUserId: String get() = chatRepository.currentUserId
}

// ── lifecycle ─────────────────────────────────────────────────────────────────

class StartChatUseCase : ChatBaseUseCase() {
    operator fun invoke() = chatRepository.start()
}

class StopChatUseCase : ChatBaseUseCase() {
    operator fun invoke() = chatRepository.stop()
}

class SetChatForegroundUseCase : ChatBaseUseCase() {
    operator fun invoke(isForeground: Boolean) = chatRepository.setForeground(isForeground)
}

/** Re-sends whatever was typed offline. Safe to call often — every send is idempotent. */
class FlushChatOutboxUseCase : ChatBaseUseCase() {
    suspend operator fun invoke() = chatRepository.flushOutbox()
}

// ── conversations ─────────────────────────────────────────────────────────────

class RefreshConversationsUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(): Flow<Result<BaseResponse<List<ChatConversation>>, Error>> =
        getBaseApiCall { chatRepository.refreshConversations() }
}

class OpenConversationUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(
        kind: ConversationKind,
        participantId: String? = null,
        title: String? = null,
    ): Flow<Result<BaseResponse<ChatConversation>, Error>> =
        getBaseApiCall { chatRepository.openConversation(kind, participantId, title) }
}

class DeleteConversationUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(conversationId: String): Flow<Result<BaseResponse<Boolean>, Error>> =
        getBaseApiCall { chatRepository.deleteConversation(conversationId) }
}

class GetChatPeersUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(query: String? = null): Flow<Result<BaseResponse<List<ChatParticipant>>, Error>> =
        getBaseApiCall { chatRepository.peers(query) }
}

// ── one thread ────────────────────────────────────────────────────────────────

/** Points the shared message flow at a thread and loads it. */
class ObserveConversationUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(conversationId: String) = chatRepository.observeConversation(conversationId)

    fun clear() = chatRepository.clearActiveConversation()
}

class LoadChatHistoryUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(
        conversationId: String,
        before: Long = 0,
    ): Flow<Result<BaseResponse<List<ChatMessage>>, Error>> =
        getBaseApiCall { chatRepository.loadHistory(conversationId, before) }
}

class SendChatMessageUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(
        conversationId: String,
        text: String,
        attachments: List<ChatAttachment> = emptyList(),
    ): Flow<Result<BaseResponse<ChatMessage>, Error>> =
        getBaseApiCall { chatRepository.sendMessage(conversationId, text, attachments) }
}

class DeleteChatMessageUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(
        conversationId: String,
        messageId: String,
    ): Flow<Result<BaseResponse<ChatMessage>, Error>> =
        getBaseApiCall { chatRepository.deleteMessage(conversationId, messageId) }
}

class MarkConversationReadUseCase : ChatBaseUseCase() {
    suspend operator fun invoke(conversationId: String) = chatRepository.markRead(conversationId)
}

class SetTypingUseCase : ChatBaseUseCase() {
    operator fun invoke(conversationId: String, isTyping: Boolean) =
        chatRepository.setTyping(conversationId, isTyping)
}
