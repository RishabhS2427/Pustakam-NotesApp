package com.app.pustakam.feature.chat.domain.repository

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ConversationKind
import kotlinx.coroutines.flow.StateFlow

/**
 * 💬 The chat's source of truth on this device. The local tables are what the screen renders; the
 * network only ever feeds them. That is why a message appears the instant it is typed and why the
 * thread is still there with no signal.
 */
interface IChatRepository {

    val conversationsState: StateFlow<List<ChatConversation>>

    /** The bottom-bar badge. */
    val totalUnreadState: StateFlow<Int>

    val connectionState: StateFlow<ChatConnectionState>

    /** conversationId → the ids of everyone currently typing in it. */
    val typingState: StateFlow<Map<String, Set<String>>>

    /** The thread currently on screen, oldest message first. */
    val activeMessagesState: StateFlow<List<ChatMessage>>

    val currentUserId: String

    /** Starts the socket and replays anything stuck in the outbox. */
    fun start()

    fun stop()

    fun setForeground(isForeground: Boolean)

    /** Points [activeMessagesState] at a thread and loads its local page immediately. */
    suspend fun observeConversation(conversationId: String)

    fun clearActiveConversation()

    suspend fun refreshConversations(): Result<BaseResponse<List<ChatConversation>>, Error>

    suspend fun openConversation(
        kind: ConversationKind,
        participantId: String? = null,
        title: String? = null,
    ): Result<BaseResponse<ChatConversation>, Error>

    suspend fun deleteConversation(conversationId: String): Result<BaseResponse<Boolean>, Error>

    /** Pulls a page of history. `before` = 0 means "the newest page". */
    suspend fun loadHistory(conversationId: String, before: Long = 0): Result<BaseResponse<List<ChatMessage>>, Error>

    /** Writes the message locally first, then sends. The returned message is the optimistic one. */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachments: List<ChatAttachment> = emptyList(),
    ): Result<BaseResponse<ChatMessage>, Error>

    /** Re-sends everything the outbox still holds. Safe to call often — sends are idempotent. */
    suspend fun flushOutbox()

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<BaseResponse<ChatMessage>, Error>

    suspend fun markRead(conversationId: String)

    fun setTyping(conversationId: String, isTyping: Boolean)

    suspend fun peers(query: String? = null): Result<BaseResponse<List<ChatParticipant>>, Error>
}
