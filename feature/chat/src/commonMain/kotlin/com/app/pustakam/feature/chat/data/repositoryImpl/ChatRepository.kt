package com.app.pustakam.feature.chat.data.repositoryImpl

import com.app.pustakam.core.common.coroutines.provideDispatcher
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.NetworkError
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.common.util.map
import com.app.pustakam.core.data.user.UserSession
import com.app.pustakam.core.database.localdb.database.ChatDao
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.chat.AiReplyState
import com.app.pustakam.core.model.models.chat.CHAT_PAGE_SIZE
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatMessageKind
import com.app.pustakam.core.model.models.chat.ChatMessageStatus
import com.app.pustakam.core.model.models.chat.ChatMessageWire
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ConversationKind
import com.app.pustakam.core.model.models.chat.MarkReadRequest
import com.app.pustakam.core.model.models.chat.OpenConversationRequest
import com.app.pustakam.core.model.models.chat.SocketReadPayload
import com.app.pustakam.core.model.models.chat.SocketTypingPayload
import com.app.pustakam.core.network.ApiCallClient
import com.app.pustakam.feature.chat.data.wire.ChatWireMapper
import com.app.pustakam.feature.chat.domain.repository.IChatRepository
import com.app.pustakam.feature.chat.domain.repository.IChatSocketRepository
import com.app.pustakam.feature.chat.domain.socket.ChatSocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val OUTBOX_LIMIT = 50
private const val PEERS_LIMIT = 100

/**
 * 💬 The local tables ARE the chat. Everything is written here first and sent second, so a bubble
 * appears the instant it is typed, survives losing signal, and is still on screen after a restart.
 *
 * Four things that will silently lose messages if they are changed:
 *
 *  1. **A send is written locally BEFORE it is sent.** Sending first and storing the response means
 *     anything typed offline is gone the moment the screen closes.
 *  2. **clientId is the identity of a send, not the row id.** A retry repeats the clientId; the
 *     server answers with the first copy. Dropping it turns every retry into a duplicate message.
 *  3. **An ack REPLACES the local id — it does not insert.** `INSERT OR REPLACE` with the server's
 *     id would leave the optimistic row behind, and the message shows up twice.
 *  4. **A failed send stays FAILED in the outbox.** Marking it sent loses the message with no trace.
 */
internal class ChatRepository : IChatRepository, KoinComponent {

    private val apiClient by inject<ApiCallClient>()
    private val chatDao by inject<ChatDao>()
    private val session by inject<UserSession>()
    private val socket by inject<IChatSocketRepository>()

    // Writes live on an app-lifetime scope, exactly like the note bridges: a screen going away
    // must not cancel a send that is already in flight.
    private val scope = CoroutineScope(SupervisorJob() + provideDispatcher().io)

    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    override val conversationsState: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    private val _totalUnread = MutableStateFlow(0)
    override val totalUnreadState: StateFlow<Int> = _totalUnread.asStateFlow()

    private val _typing = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    override val typingState: StateFlow<Map<String, Set<String>>> = _typing.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val activeMessagesState: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    override val connectionState: StateFlow<ChatConnectionState> get() = socket.connectionState

    override val currentUserId: String get() = session.userId

    private var activeConversationId: String? = null
    // 📜 how many messages the active thread has loaded. Paging back widens this window rather
    //   than merging lists in the UI, so the flow stays the single source of truth.
    private var activeWindow = CHAT_PAGE_SIZE
    private var started = false

    // ── lifecycle ─────────────────────────────────────────────────────────────

    override fun start() {
        if (!started) {
            started = true
            scope.launch { socket.events.collect { onSocketEvent(it) } }
        }
        _conversations.value = chatDao.conversations()
        _totalUnread.value = chatDao.totalUnread()
        socket.connect()
        scope.launch { flushOutbox() }
    }

    override fun stop() {
        socket.disconnect()
    }

    override fun setForeground(isForeground: Boolean) {
        socket.setForeground(isForeground)
        // Coming back to a chat with a live socket still needs the outbox drained
        if (isForeground) scope.launch { flushOutbox() }
    }

    override suspend fun observeConversation(conversationId: String) {
        activeConversationId = conversationId
        activeWindow = CHAT_PAGE_SIZE
        _activeMessages.value = chatDao.messagesPage(conversationId, CHAT_PAGE_SIZE, page = 1)
        loadHistory(conversationId)
        markRead(conversationId)
    }

    override fun clearActiveConversation() {
        activeConversationId = null
        activeWindow = CHAT_PAGE_SIZE
        _activeMessages.value = emptyList()
    }

    // ── conversations ─────────────────────────────────────────────────────────

    override suspend fun refreshConversations(): Result<BaseResponse<List<ChatConversation>>, Error> =
        apiClient.listConversations(limit = CHAT_PAGE_SIZE).map { response ->
            val conversations = response.data.orEmpty().map { ChatWireMapper.toDomain(it) }
            chatDao.upsertConversations(conversations)
            refreshConversationFlows()
            BaseResponse(data = conversations, isSuccessful = true, message = response.message)
        }

    override suspend fun openConversation(
        kind: ConversationKind,
        participantId: String?,
        title: String?,
    ): Result<BaseResponse<ChatConversation>, Error> =
        apiClient.openConversation(
            OpenConversationRequest(kind = kind.name, participantId = participantId, title = title)
        ).map { response ->
            val conversation = response.data?.let { ChatWireMapper.toDomain(it) }
            conversation?.let {
                chatDao.upsertConversation(it)
                refreshConversationFlows()
            }
            BaseResponse(data = conversation, isSuccessful = conversation != null, message = response.message)
        }

    override suspend fun deleteConversation(conversationId: String): Result<BaseResponse<Boolean>, Error> {
        // Local first so the row disappears immediately; the server call only confirms it
        chatDao.softDeleteConversation(conversationId)
        refreshConversationFlows()
        if (activeConversationId == conversationId) clearActiveConversation()

        return apiClient.deleteConversation(conversationId).map { response ->
            BaseResponse(data = response.data?.deleted ?: true, isSuccessful = true, message = response.message)
        }
    }

    override suspend fun peers(query: String?): Result<BaseResponse<List<ChatParticipant>>, Error> =
        apiClient.getChatPeers(query = query, limit = PEERS_LIMIT).map { response ->
            BaseResponse(
                data = response.data.orEmpty().map { ChatWireMapper.toDomain(it) },
                isSuccessful = true,
                message = response.message,
            )
        }

    // ── history ───────────────────────────────────────────────────────────────

    override suspend fun loadHistory(conversationId: String, before: Long): Result<BaseResponse<List<ChatMessage>>, Error> =
        apiClient.getMessages(
            conversationId = conversationId,
            limit = CHAT_PAGE_SIZE,
            before = before.takeIf { it > 0 },
        ).map { response ->
            val messages = response.data.orEmpty().map { wire ->
                // localPath is restored from the row already here, or a pull orphans files on disk
                ChatWireMapper.restoreLocalPaths(ChatWireMapper.toDomain(wire), chatDao.message(wire.id))
            }
            chatDao.upsertMessages(messages)
            // Scrolling back widens the window so the newly stored older rows stay on screen
            if (before > 0) activeWindow += CHAT_PAGE_SIZE
            if (conversationId == activeConversationId) refreshActiveMessages()
            BaseResponse(data = messages, isSuccessful = true, message = response.message)
        }

    // ── sending ───────────────────────────────────────────────────────────────

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachments: List<ChatAttachment>,
    ): Result<BaseResponse<ChatMessage>, Error> {
        val body = text.trim()
        if (body.isEmpty() && attachments.isEmpty()) {
            return Result.Error(NetworkError.BAD_REQUEST)
        }

        // 1️⃣ the optimistic row. id and clientId are the same value on purpose: the id is what the
        //    UI keys on, the clientId is what the server dedupes on, and one value keeps them paired.
        val localId = UniqueIdGenerator.generateUniqueId()
        val timestamp = getCurrentTimestamp()
        val message = ChatMessage(
            id = localId,
            conversationId = conversationId,
            senderId = session.userId,
            kind = ChatMessageKind.USER,
            text = body,
            attachments = attachments,
            clientId = localId,
            status = ChatMessageStatus.SENDING,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        chatDao.upsertMessageAndTouch(message)
        refreshActiveMessages()
        refreshConversationFlows()

        // 2️⃣ socket first. The ack comes back through onSocketEvent and settles the row there.
        if (socket.send(frameId = localId, payload = ChatWireMapper.toSocketSend(message))) {
            return Result.Success(BaseResponse(data = message, isSuccessful = true))
        }

        // 3️⃣ no socket — REST, which is a complete send on its own
        return deliverOverRest(message)
    }

    /** REST fallback. A failure leaves the row FAILED and in the outbox, never quietly "sent". */
    private suspend fun deliverOverRest(message: ChatMessage): Result<BaseResponse<ChatMessage>, Error> =
        when (val result = apiClient.sendMessage(message.conversationId, ChatWireMapper.toSendRequest(message))) {
            is Result.Success -> {
                val stored = result.data.data?.let { settleAck(message.id, it) } ?: message
                Result.Success(BaseResponse(data = stored, isSuccessful = true))
            }
            is Result.Error -> {
                chatDao.updateStatus(message.id, ChatMessageStatus.FAILED)
                refreshActiveMessages()
                result
            }
            is Result.Loading -> Result.Loading
        }

    override suspend fun flushOutbox() {
        val pending = chatDao.pendingMessages(OUTBOX_LIMIT)
        if (pending.isEmpty()) return

        for (message in pending) {
            // The clientId is what makes a replay safe — the server returns the first copy
            if (socket.send(frameId = message.id, payload = ChatWireMapper.toSocketSend(message))) continue
            val result = deliverOverRest(message)
            // A refused send stops the drain: the rest are almost certainly going to fail too, and
            // hammering a dead server just burns battery. The next flush picks up where this left off.
            if (result is Result.Error) {
                log_d("ChatRepository", "outbox stalled at ${message.id}: ${result.error}")
                break
            }
        }
    }

    override suspend fun deleteMessage(conversationId: String, messageId: String): Result<BaseResponse<ChatMessage>, Error> {
        chatDao.softDeleteMessage(messageId)
        refreshActiveMessages()
        return apiClient.deleteMessage(conversationId, messageId).map { response ->
            BaseResponse(
                data = response.data?.let { ChatWireMapper.toDomain(it) },
                isSuccessful = true,
                message = response.message,
            )
        }
    }

    // ── receipts and typing ───────────────────────────────────────────────────

    override suspend fun markRead(conversationId: String) {
        val readAt = getCurrentTimestamp()
        chatDao.setUnread(conversationId, 0)
        refreshConversationFlows()

        val frameId = UniqueIdGenerator.generateUniqueId()
        val payload = SocketReadPayload(conversationId = conversationId, lastReadAt = readAt)
        if (socket.sendRead(frameId, payload)) return

        apiClient.markConversationRead(conversationId, MarkReadRequest(lastReadAt = readAt))
    }

    override fun setTyping(conversationId: String, isTyping: Boolean) {
        // Typing is a courtesy, not data. If the socket is down it is simply not sent — there is
        // no REST fallback, because a typing indicator that arrives late is worse than none.
        socket.sendTyping(SocketTypingPayload(conversationId = conversationId, isTyping = isTyping))
    }

    // ── inbound ───────────────────────────────────────────────────────────────

    private suspend fun onSocketEvent(event: ChatSocketEvent) {
        when (event) {
            is ChatSocketEvent.Connected -> flushOutbox()

            is ChatSocketEvent.Ready -> refreshConversations()

            is ChatSocketEvent.MessageReceived -> applyIncoming(event.message)

            is ChatSocketEvent.Ack -> {
                val local = event.frameId ?: event.clientId
                val wire = event.message
                if (local != null && wire != null) settleAck(local, wire)
            }

            is ChatSocketEvent.Typing -> applyTyping(event.conversationId, event.userId, event.isTyping)

            is ChatSocketEvent.Read -> {
                // The other side read my messages: my own bubbles turn to READ
                chatDao.markOutgoingRead(event.receipt.conversationId, session.userId, event.receipt.lastReadAt)
                refreshActiveMessages()
            }

            is ChatSocketEvent.AiDelta -> applyAiDelta(event.conversationId, event.messageId, event.delta)

            is ChatSocketEvent.AiDone -> event.message?.let { applyIncoming(it) }

            is ChatSocketEvent.Failure -> {
                // A frame the server refused belongs to a message that is still in the outbox
                event.frameId?.let { chatDao.updateStatus(it, ChatMessageStatus.FAILED) }
                refreshActiveMessages()
                log_d("ChatRepository", "socket refused ${event.frameId}: ${event.code} ${event.message}")
            }

            is ChatSocketEvent.Disconnected, is ChatSocketEvent.Pong, is ChatSocketEvent.Unsupported -> Unit
        }
    }

    /** Stores a message that arrived from anyone, including this user's own on another device. */
    private suspend fun applyIncoming(wire: ChatMessageWire) {
        val incoming = ChatWireMapper.toDomain(wire)

        // My own send echoed back: settle the optimistic row rather than adding a second one
        val localByClientId = wire.clientId?.let { chatDao.messageByClientId(wire.conversationId, it) }
        if (localByClientId != null && localByClientId.id != incoming.id) {
            settleAck(localByClientId.id, wire)
            return
        }

        val stored = ChatWireMapper.restoreLocalPaths(incoming, chatDao.message(incoming.id))
        chatDao.upsertMessageAndTouch(stored)

        // Anything arriving in a thread that is not on screen, from someone else, is unread
        val conversation = chatDao.conversation(stored.conversationId)
        val isMine = stored.senderId == session.userId
        val isActive = stored.conversationId == activeConversationId
        if (!isMine && !isActive && conversation != null) {
            chatDao.setUnread(stored.conversationId, conversation.unreadCount + 1)
        }

        refreshConversationFlows()
        if (isActive) refreshActiveMessages()

        // First message of a conversation this device has never seen — without this the thread
        // exists but the inbox row has no name, no avatar and no way to be opened.
        if (conversation == null) refreshConversations()
    }

    /**
     * The optimistic row becomes the stored one: the server's id replaces the local id in place.
     * Inserting instead of replacing is how a sent message ends up on screen twice.
     */
    private fun settleAck(localId: String, wire: ChatMessageWire): ChatMessage {
        val server = ChatWireMapper.toDomain(wire)
        val local = chatDao.message(localId)
        val merged = ChatWireMapper.restoreLocalPaths(server, local)

        chatDao.adoptServerId(
            localId = localId,
            serverId = merged.id,
            status = merged.status,
            serverUpdatedAt = merged.serverUpdatedAt,
        )
        chatDao.upsertMessageAndTouch(merged)
        refreshActiveMessages()
        refreshConversationFlows()
        return merged
    }

    /** 🤖 a reply that is still being written grows in place, in the DB and on screen. */
    private fun applyAiDelta(conversationId: String, messageId: String, delta: String) {
        val existing = chatDao.message(messageId) ?: return
        val grown = existing.appendingDelta(delta)
        chatDao.updateText(messageId, grown.text, AiReplyState.STREAMING, null)
        if (conversationId == activeConversationId) refreshActiveMessages()
    }

    private fun applyTyping(conversationId: String, userId: String, isTyping: Boolean) {
        if (userId == session.userId) return
        _typing.update { current ->
            val existing = current[conversationId].orEmpty()
            val next = if (isTyping) existing + userId else existing - userId
            if (next.isEmpty()) current - conversationId else current + (conversationId to next)
        }
    }

    // ── flow refresh ──────────────────────────────────────────────────────────

    private fun refreshConversationFlows() {
        _conversations.value = chatDao.conversations()
        _totalUnread.value = chatDao.totalUnread()
    }

    private fun refreshActiveMessages() {
        val conversationId = activeConversationId ?: return
        _activeMessages.value =
            chatDao.messagesPage(conversationId, activeWindow.coerceAtLeast(CHAT_PAGE_SIZE), page = 1)
    }
}
