package com.app.pustakam.feature.chat.domain.bridge

import com.app.pustakam.core.common.bridge.BridgeError
import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.data.bridge.subscribeTo
import com.app.pustakam.core.data.bridge.watch
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.core.model.models.chat.ConversationKind
import com.app.pustakam.feature.chat.domain.usecase.DeleteChatMessageUseCase
import com.app.pustakam.feature.chat.domain.usecase.DeleteConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.FlushChatOutboxUseCase
import com.app.pustakam.feature.chat.domain.usecase.GetChatPeersUseCase
import com.app.pustakam.feature.chat.domain.usecase.LoadChatHistoryUseCase
import com.app.pustakam.feature.chat.domain.usecase.MarkConversationReadUseCase
import com.app.pustakam.feature.chat.domain.usecase.ObserveConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.OpenConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.RefreshConversationsUseCase
import com.app.pustakam.feature.chat.domain.usecase.SendChatMessageUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetChatForegroundUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetTypingUseCase
import com.app.pustakam.feature.chat.domain.usecase.StartChatUseCase
import com.app.pustakam.feature.chat.domain.usecase.StopChatUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 💬 iOS's entry point into the chat. Use cases only — the same rule NotesBridge and AuthBridge
 * follow, so Swift never touches a repository and never has to understand Result.
 *
 * Two scopes on purpose, exactly as in the note bridges:
 *   • `scope`      — observers. dispose() cancels these when the screen goes away.
 *   • `writeScope` — sends. A SwiftUI View struct being recreated must NEVER cancel a message
 *                    that is already on its way.
 */
class ChatBridge : KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private val startChat: StartChatUseCase by inject()
    private val stopChat: StopChatUseCase by inject()
    private val setForeground: SetChatForegroundUseCase by inject()
    private val flushOutbox: FlushChatOutboxUseCase by inject()
    private val refreshConversations: RefreshConversationsUseCase by inject()
    private val openConversationUseCase: OpenConversationUseCase by inject()
    private val deleteConversationUseCase: DeleteConversationUseCase by inject()
    private val observeConversationUseCase: ObserveConversationUseCase by inject()
    private val loadHistory: LoadChatHistoryUseCase by inject()
    private val sendMessage: SendChatMessageUseCase by inject()
    private val deleteMessageUseCase: DeleteChatMessageUseCase by inject()
    private val markRead: MarkConversationReadUseCase by inject()
    private val setTypingUseCase: SetTypingUseCase by inject()
    private val peersUseCase: GetChatPeersUseCase by inject()

    val currentUserId: String get() = startChat.currentUserId

    // ── lifecycle ─────────────────────────────────────────────────────────────

    fun start() = startChat()

    fun stop() = stopChat()

    /** Called from scenePhase: the socket drops in the background and comes back on foreground. */
    fun setAppForeground(isForeground: Boolean) = setForeground(isForeground)

    fun retryPending() {
        writeScope.launch { flushOutbox() }
    }

    // ── observation ───────────────────────────────────────────────────────────

    fun observeConversations(onChange: (List<ChatConversation>) -> Unit): Closeable =
        startChat.conversations.watch(scope) { onChange(it) }

    /** The thread currently opened with [openThread]. */
    fun observeMessages(onChange: (List<ChatMessage>) -> Unit): Closeable =
        startChat.messages.watch(scope) { onChange(it) }

    fun observeConnection(onChange: (ChatConnectionState) -> Unit): Closeable =
        startChat.connection.watch(scope) { onChange(it) }

    fun observeTotalUnread(onChange: (Int) -> Unit): Closeable =
        startChat.totalUnread.watch(scope) { onChange(it) }

    /** Swift gets the ids typing in ONE conversation, not the whole map. */
    fun observeTyping(conversationId: String, onChange: (List<String>) -> Unit): Closeable =
        startChat.typing.watch(scope) { onChange(it[conversationId].orEmpty().toList()) }

    // ── conversations ─────────────────────────────────────────────────────────

    fun refresh(
        onLoading: () -> Unit,
        onSuccess: (List<ChatConversation>?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(scope, { refreshConversations() }, onLoading, onSuccess, onError)

    fun openDirectConversation(
        participantId: String,
        onLoading: () -> Unit,
        onSuccess: (ChatConversation?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(
        writeScope,
        { openConversationUseCase(ConversationKind.DIRECT, participantId, null) },
        onLoading, onSuccess, onError
    )

    fun openAssistantConversation(
        title: String?,
        onLoading: () -> Unit,
        onSuccess: (ChatConversation?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(
        writeScope,
        { openConversationUseCase(ConversationKind.AI, null, title) },
        onLoading, onSuccess, onError
    )

    fun deleteConversation(
        conversationId: String,
        onLoading: () -> Unit,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(
        writeScope, { deleteConversationUseCase(conversationId) }, onLoading, onSuccess, onError
    )

    fun peers(
        query: String?,
        onLoading: () -> Unit,
        onSuccess: (List<ChatParticipant>?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(scope, { peersUseCase(query) }, onLoading, onSuccess, onError)

    // ── one thread ────────────────────────────────────────────────────────────

    /** Points observeMessages at this thread, loads it and marks it read. */
    fun openThread(conversationId: String) {
        writeScope.launch { observeConversationUseCase(conversationId) }
    }

    fun closeThread() = observeConversationUseCase.clear()

    fun loadOlder(
        conversationId: String,
        before: Long,
        onLoading: () -> Unit,
        onSuccess: (List<ChatMessage>?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(scope, { loadHistory(conversationId, before) }, onLoading, onSuccess, onError)

    /** A send must outlive the view that started it, so it runs on writeScope. */
    fun send(
        conversationId: String,
        text: String,
        attachments: List<ChatAttachment>,
        onLoading: () -> Unit,
        onSuccess: (ChatMessage?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(
        writeScope, { sendMessage(conversationId, text, attachments) }, onLoading, onSuccess, onError
    )

    fun deleteMessage(
        conversationId: String,
        messageId: String,
        onLoading: () -> Unit,
        onSuccess: (ChatMessage?) -> Unit,
        onError: (BridgeError) -> Unit,
    ): Closeable = subscribeTo(
        writeScope, { deleteMessageUseCase(conversationId, messageId) }, onLoading, onSuccess, onError
    )

    fun markConversationRead(conversationId: String) {
        writeScope.launch { markRead(conversationId) }
    }

    fun setTyping(conversationId: String, isTyping: Boolean) = setTypingUseCase(conversationId, isTyping)

    fun dispose() = scope.cancel()
}
