package com.app.pustakam.android.screen.chat

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.CHAT
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.feature.chat.domain.presentation.ChatIntent
import com.app.pustakam.feature.chat.domain.presentation.ChatReducer
import com.app.pustakam.feature.chat.domain.presentation.ChatState
import com.app.pustakam.feature.chat.domain.usecase.DeleteChatMessageUseCase
import com.app.pustakam.feature.chat.domain.usecase.LoadChatHistoryUseCase
import com.app.pustakam.feature.chat.domain.usecase.MarkConversationReadUseCase
import com.app.pustakam.feature.chat.domain.usecase.ObserveConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.SendChatMessageUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetChatForegroundUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetTypingUseCase
import com.app.pustakam.feature.chat.domain.usecase.StartChatUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject

private const val TYPING_IDLE_MILLIS = 2_500L

/**
 * 💬 The thread. Every state change goes through the SHARED ChatReducer, so Android and iOS cannot
 * drift apart — and so the behaviour is testable in commonTest without a device.
 */
class ChatViewModel : BaseViewModel() {

    private val startChat by inject<StartChatUseCase>()
    private val observeConversation by inject<ObserveConversationUseCase>()
    private val loadHistory by inject<LoadChatHistoryUseCase>()
    private val sendMessage by inject<SendChatMessageUseCase>()
    private val deleteMessageUseCase by inject<DeleteChatMessageUseCase>()
    private val markRead by inject<MarkConversationReadUseCase>()
    private val setTypingUseCase by inject<SetTypingUseCase>()
    private val setForeground by inject<SetChatForegroundUseCase>()

    private val _state = MutableStateFlow(ChatState(currentUserId = startChat.currentUserId))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var typingJob: Job? = null
    private var openedConversationId: String? = null

    init {
        // The socket, the message flow, the typing map and the connection state are all owned by
        // the repository — this ViewModel only ever mirrors them into the reduced state.
        viewModelScope.launch { startChat.messages.collect { emit(ChatIntent.MessagesLoaded(it)) } }
        viewModelScope.launch { startChat.connection.collect { emit(ChatIntent.ConnectionChanged(it)) } }
        viewModelScope.launch {
            startChat.typing.collect { typing ->
                val conversationId = _state.value.conversationId ?: return@collect
                emit(ChatIntent.TypingChanged(typing[conversationId].orEmpty()))
            }
        }
        viewModelScope.launch {
            startChat.conversations.collect { conversations ->
                val conversationId = _state.value.conversationId ?: return@collect
                conversations.firstOrNull { it.id == conversationId }
                    ?.let { emit(ChatIntent.ConversationLoaded(it)) }
            }
        }
    }

    /** Idempotent: re-entering the same thread must not reload it and lose the scroll position. */
    fun open(conversationId: String) {
        if (openedConversationId == conversationId) return
        openedConversationId = conversationId

        emit(ChatIntent.Open(conversationId))
        emit(ChatIntent.CurrentUserResolved(startChat.currentUserId))
        startChat()
        viewModelScope.launch { observeConversation(conversationId) }
    }

    fun onForeground(isForeground: Boolean) {
        setForeground(isForeground)
        if (isForeground) _state.value.conversationId?.let { viewModelScope.launch { markRead(it) } }
    }

    /** Called when the screen goes away, so the next thread does not inherit this one's messages. */
    fun close() {
        openedConversationId = null
        observeConversation.clear()
        setTyping(isTyping = false)
    }

    // ── composer ──────────────────────────────────────────────────────────────

    fun onDraftChange(text: String) {
        emit(ChatIntent.DraftChanged(text))
        signalTyping(text.isNotBlank())
    }

    fun onAttachmentAdded(attachment: ChatAttachment) = emit(ChatIntent.AttachmentAdded(attachment))

    fun onAttachmentRemoved(attachment: ChatAttachment) = emit(ChatIntent.AttachmentRemoved(attachment))

    fun send() {
        val current = _state.value
        val conversationId = current.conversationId ?: return
        if (!current.canSend) return

        val text = current.draft
        val attachments = current.pendingAttachments
        // The reducer clears the composer NOW — the optimistic bubble is already on its way
        emit(ChatIntent.SendRequested)
        setTyping(isTyping = false)

        makeAWish<ChatMessage>(CHAT.SEND, showLoader = false) {
            sendMessage(conversationId, text, attachments)
        }
    }

    fun loadOlder() {
        val current = _state.value
        val conversationId = current.conversationId ?: return
        if (current.isLoadingOlder || !current.hasMoreHistory) return

        emit(ChatIntent.OlderRequested(current.oldestMessageAt))
        makeAWish<List<ChatMessage>>(CHAT.OLDER, showLoader = false) {
            loadHistory(conversationId, current.oldestMessageAt)
        }
    }

    fun deleteMessage(message: ChatMessage) {
        val conversationId = _state.value.conversationId ?: return
        makeAWish<ChatMessage>(CHAT.DELETE_MESSAGE, showLoader = false) {
            deleteMessageUseCase(conversationId, message.id)
        }
    }

    fun markThreadRead() {
        val conversationId = _state.value.conversationId ?: return
        viewModelScope.launch { markRead(conversationId) }
    }

    // ── typing ────────────────────────────────────────────────────────────────

    /**
     * Typing is debounced into "started" and "stopped after a pause". Sending a frame per keystroke
     * would put one websocket message on the wire per letter.
     */
    private fun signalTyping(isTyping: Boolean) {
        val conversationId = _state.value.conversationId ?: return
        typingJob?.cancel()
        if (!isTyping) {
            setTypingUseCase(conversationId, false)
            return
        }
        setTypingUseCase(conversationId, true)
        typingJob = viewModelScope.launch {
            delay(TYPING_IDLE_MILLIS)
            setTypingUseCase(conversationId, false)
        }
    }

    private fun setTyping(isTyping: Boolean) {
        typingJob?.cancel()
        _state.value.conversationId?.let { setTypingUseCase(it, isTyping) }
    }

    // ── BaseViewModel ─────────────────────────────────────────────────────────

    private fun emit(intent: ChatIntent) = _state.update { ChatReducer.reduce(it, intent) }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        when (taskCode) {
            CHAT.SEND -> emit(ChatIntent.SendCompleted)
            // The message list itself arrives through the repository flow, so only the paging
            // bookkeeping is settled here — otherwise the same page lands twice.
            CHAT.OLDER -> emit(
                ChatIntent.OlderLoaded((result.data.data as? List<*>)?.filterIsInstance<ChatMessage>().orEmpty())
            )
            else -> Unit
        }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        emit(ChatIntent.Failed(error.displayMessage()))
    }

    override fun clearError() = emit(ChatIntent.ErrorCleared)

    override fun onCleared() {
        super.onCleared()
        close()
    }
}
