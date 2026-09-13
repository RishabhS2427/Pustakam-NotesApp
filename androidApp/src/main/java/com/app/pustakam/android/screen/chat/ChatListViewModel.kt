package com.app.pustakam.android.screen.chat

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.CHAT
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatParticipant
import com.app.pustakam.feature.chat.domain.presentation.ChatListIntent
import com.app.pustakam.feature.chat.domain.presentation.ChatListState
import com.app.pustakam.feature.chat.domain.presentation.ChatReducer
import com.app.pustakam.feature.chat.domain.usecase.DeleteConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.GetChatPeersUseCase
import com.app.pustakam.feature.chat.domain.usecase.OpenConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.RefreshConversationsUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetChatForegroundUseCase
import com.app.pustakam.feature.chat.domain.usecase.StartChatUseCase
import com.app.pustakam.core.model.models.chat.ConversationKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject

private const val PEER_SEARCH_DEBOUNCE_MILLIS = 400L

/** 💬 The inbox. Conversations, the unread badge, and the picker for starting a new chat. */
class ChatListViewModel : BaseViewModel() {

    private val startChat by inject<StartChatUseCase>()
    private val refreshConversations by inject<RefreshConversationsUseCase>()
    private val openConversationUseCase by inject<OpenConversationUseCase>()
    private val deleteConversationUseCase by inject<DeleteConversationUseCase>()
    private val peersUseCase by inject<GetChatPeersUseCase>()
    private val setForeground by inject<SetChatForegroundUseCase>()

    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    /** Set when a new conversation has been opened, so the screen can navigate into it. */
    private val _openedConversationId = MutableStateFlow<String?>(null)
    val openedConversationId: StateFlow<String?> = _openedConversationId.asStateFlow()

    private var hasLoaded = false
    private var peerSearchJob: Job? = null

    init {
        viewModelScope.launch { startChat.conversations.collect { emit(ChatListIntent.ConversationsLoaded(it)) } }
        viewModelScope.launch { startChat.totalUnread.collect { emit(ChatListIntent.UnreadChanged(it)) } }
        viewModelScope.launch { startChat.connection.collect { emit(ChatListIntent.ConnectionChanged(it)) } }
    }

    /** One-time load, like the notes list. Live updates arrive on the flows above. */
    fun load() {
        startChat()
        if (hasLoaded) return
        hasLoaded = true
        emit(ChatListIntent.Load)
        makeAWish<List<ChatConversation>>(CHAT.CONVERSATIONS, showLoader = false) { refreshConversations() }
    }

    fun onForeground(isForeground: Boolean) = setForeground(isForeground)

    fun refresh() {
        emit(ChatListIntent.RefreshRequested)
        makeAWish<List<ChatConversation>>(CHAT.CONVERSATIONS, showLoader = false) { refreshConversations() }
    }

    /**
     * 🆔 31-Aug-2026 — the query now drives a SERVER search by username prefix, debounced, instead
     * of filtering a downloaded directory of everyone. A blank query returns nothing by design:
     * there is no browsable list of users any more.
     */
    fun onQueryChange(query: String) {
        peerSearchJob?.cancel()
        emit(ChatListIntent.QueryChanged(query))

        if (query.isBlank()) {
            emit(ChatListIntent.PeersLoaded(emptyList()))
            return
        }
        emit(ChatListIntent.PeersSearching(true))
        peerSearchJob = viewModelScope.launch {
            delay(PEER_SEARCH_DEBOUNCE_MILLIS)
            makeAWish<List<ChatParticipant>>(CHAT.PEERS, showLoader = false) { peersUseCase(query) }
        }
    }

    fun openPeerPicker() = emit(ChatListIntent.PeerPickerToggled(true))

    fun closePeerPicker() {
        peerSearchJob?.cancel()
        emit(ChatListIntent.PeerPickerToggled(false))
    }

    fun startDirectChat(participantId: String) {
        makeAWish<ChatConversation>(CHAT.OPEN_CONVERSATION, showLoader = false) {
            openConversationUseCase(ConversationKind.DIRECT, participantId, null)
        }
    }

    /** 🤖 a fresh thread with the assistant. Several are allowed — they never collide server-side. */
    fun startAssistantChat() {
        makeAWish<ChatConversation>(CHAT.OPEN_CONVERSATION, showLoader = false) {
            openConversationUseCase(ConversationKind.AI, null, null)
        }
    }

    fun deleteConversation(conversationId: String) {
        makeAWish<Boolean>(CHAT.DELETE_CONVERSATION, showLoader = false) { deleteConversationUseCase(conversationId) }
    }

    /** Cleared once the screen has navigated, or reopening the inbox jumps straight back in. */
    fun consumeOpenedConversation() {
        _openedConversationId.value = null
    }

    private fun emit(intent: ChatListIntent) = _state.update { ChatReducer.reduceList(it, intent) }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        when (taskCode) {
            CHAT.PEERS -> emit(
                ChatListIntent.PeersLoaded(
                    (result.data.data as? List<*>)?.filterIsInstance<ChatParticipant>().orEmpty()
                )
            )
            CHAT.OPEN_CONVERSATION -> {
                val conversation = result.data.data as? ChatConversation ?: return
                emit(ChatListIntent.PeerPickerToggled(false))
                _openedConversationId.value = conversation.id
            }
            // The list itself arrives through the conversations flow — a single source of truth
            CHAT.CONVERSATIONS -> emit(ChatListIntent.ConversationsLoaded(startChat.conversations.value))
            else -> Unit
        }
    }

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        emit(ChatListIntent.Failed(error.displayMessage()))
    }

    override fun clearError() = emit(ChatListIntent.ErrorCleared)
}
