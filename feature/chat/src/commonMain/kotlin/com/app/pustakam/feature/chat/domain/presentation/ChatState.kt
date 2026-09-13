package com.app.pustakam.feature.chat.domain.presentation

import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatParticipant

/**
 * 💬 One state, one reducer, both platforms. Android's ViewModel and iOS's ObservableObject apply
 * the SAME transitions to the SAME shape — which is what stops the two chats drifting apart, and
 * what makes the behaviour testable without a device.
 */
data class ChatState(
    val conversationId: String? = null,
    val conversation: ChatConversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val typingUserIds: Set<String> = emptySet(),
    val connection: ChatConnectionState = ChatConnectionState.DISCONNECTED,
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreHistory: Boolean = true,
    val error: String? = null,
) {
    val canSend: Boolean
        get() = !isSending && (draft.isNotBlank() || pendingAttachments.isNotEmpty())

    val isEmpty: Boolean get() = messages.isEmpty() && !isLoading

    val isSomeoneTyping: Boolean get() = typingUserIds.isNotEmpty()

    /** The banner only earns its space when something is actually wrong. */
    val showsConnectionBanner: Boolean
        get() = connection == ChatConnectionState.DISCONNECTED || connection == ChatConnectionState.RECONNECTING

    val isAssistantThread: Boolean get() = conversation?.isAssistant() == true

    /** 🤖 the assistant's half-written reply, if one is in flight. */
    val streamingMessage: ChatMessage? get() = messages.lastOrNull()?.takeIf { it.isStreaming() }

    val title: String get() = conversation?.displayTitle().orEmpty()

    /** The timestamp to page back from. 0 when there is nothing on screen yet. */
    val oldestMessageAt: Long get() = messages.firstOrNull()?.createdAt ?: 0

    fun isMine(message: ChatMessage): Boolean = message.isMine(currentUserId)
}

/** The inbox screen. Separate from [ChatState] because they share nothing but the repository. */
data class ChatListState(
    val conversations: List<ChatConversation> = emptyList(),
    val peers: List<ChatParticipant> = emptyList(),
    val query: String = "",
    val connection: ChatConnectionState = ChatConnectionState.DISCONNECTED,
    val totalUnread: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPickingPeer: Boolean = false,
    // 🆔 31-Aug-2026 — the picker searches the server now, so it has a spinner of its own
    val isSearchingPeers: Boolean = false,
    val error: String? = null,
) {
    val visibleConversations: List<ChatConversation>
        get() = if (query.isBlank()) conversations
        else conversations.filter { it.displayTitle().contains(query, ignoreCase = true) }

    /**
     * 🆔 31-Aug-2026 — NOT filtered locally any more. `peers` is now the answer to a server-side
     * username-prefix search, so filtering it again by display name would hide the very rows the
     * search found: searching "rish" returns @rishabh, whose name may be "R. S." and contains no
     * "rish". The conversation list above still filters locally, because that list IS local.
     */
    val visiblePeers: List<ChatParticipant> get() = peers

    val isEmpty: Boolean get() = conversations.isEmpty() && !isLoading

    /** The picker has nothing to show and nothing to wait for — prompt, don't say "no results". */
    val isPeerSearchIdle: Boolean get() = query.isBlank() && !isSearchingPeers
}
