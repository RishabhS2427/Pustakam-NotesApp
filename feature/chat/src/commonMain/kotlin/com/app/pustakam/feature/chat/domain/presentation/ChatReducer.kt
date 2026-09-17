package com.app.pustakam.feature.chat.domain.presentation

import com.app.pustakam.core.model.models.chat.CHAT_PAGE_SIZE

/**
 * 💬 Pure. No coroutines, no Koin, no platform. Every state transition the chat screen makes is
 * decided here, so both platforms behave identically and the behaviour can be tested without a
 * device, a socket or a server.
 */
object ChatReducer {

    fun reduce(state: ChatState, intent: ChatIntent): ChatState = when (intent) {

        is ChatIntent.Open -> ChatState(
            conversationId = intent.conversationId,
            currentUserId = state.currentUserId,
            connection = state.connection,
            isLoading = true,
        )

        ChatIntent.LoadStarted -> state.copy(isLoading = true, error = null)

        is ChatIntent.ConversationLoaded -> state.copy(conversation = intent.conversation, isLoading = false)

        is ChatIntent.MessagesLoaded -> state.copy(
            messages = intent.messages,
            isLoading = false,
            error = null,
        )

        is ChatIntent.OlderRequested -> state.copy(isLoadingOlder = true)

        // Paging bookkeeping ONLY. The list itself arrives through the repository's message
        // flow — the same single-source-of-truth rule the notes list follows. Merging here as well
        // would fight that flow and make a page appear twice.
        // A short page means the thread has no more history; asking forever is what this prevents.
        is ChatIntent.OlderLoaded -> state.copy(
            isLoadingOlder = false,
            hasMoreHistory = intent.messages.size >= CHAT_PAGE_SIZE,
        )

        is ChatIntent.DraftChanged -> state.copy(draft = intent.text)

        is ChatIntent.AttachmentAdded -> state.copy(
            pendingAttachments = state.pendingAttachments + intent.attachment
        )

        is ChatIntent.AttachmentRemoved -> state.copy(
            pendingAttachments = state.pendingAttachments - intent.attachment
        )

        // The composer clears here, not on the response: the optimistic bubble is already on screen,
        // and leaving the text behind makes it look like nothing happened.
        ChatIntent.SendRequested -> if (!state.canSend) state else state.copy(
            draft = "",
            pendingAttachments = emptyList(),
            isSending = true,
            error = null,
        )

        ChatIntent.SendCompleted -> state.copy(isSending = false)

        is ChatIntent.TypingChanged -> state.copy(typingUserIds = intent.userIds - state.currentUserId)

        is ChatIntent.ConnectionChanged -> state.copy(connection = intent.state)

        is ChatIntent.CurrentUserResolved -> state.copy(currentUserId = intent.userId)

        is ChatIntent.Failed -> state.copy(isLoading = false, isSending = false, isLoadingOlder = false, error = intent.message)

        ChatIntent.ErrorCleared -> state.copy(error = null)
    }

    // Deliberately NOT an overload: Objective-C cannot overload on parameter type, so a second
    // `reduce` would reach Swift as the unreadable `reduce(state:intent_:)`.
    fun reduceList(state: ChatListState, intent: ChatListIntent): ChatListState = when (intent) {

        ChatListIntent.Load -> state.copy(isLoading = true, error = null)

        // Refreshing is deliberately NOT isLoading: that blanks the screen, and a refresh must
        // leave the list readable while it runs. Same rule as the notes list.
        ChatListIntent.RefreshRequested -> state.copy(isRefreshing = true, error = null)

        is ChatListIntent.ConversationsLoaded -> state.copy(
            conversations = intent.conversations,
            isLoading = false,
            isRefreshing = false,
            error = null,
        )

        is ChatListIntent.PeersLoaded -> state.copy(peers = intent.peers, isSearchingPeers = false)

        is ChatListIntent.PeersSearching -> state.copy(isSearchingPeers = intent.isSearching)

        is ChatListIntent.QueryChanged -> state.copy(query = intent.query)

        is ChatListIntent.InboxQueryChanged -> state.copy(inboxQuery = intent.query)

        // 🔧 31-Aug-2026 — the query is cleared on CLOSE too. It used to survive, which left the
        //   inbox silently filtered by whatever was typed in the picker, with no field to clear.
        is ChatListIntent.PeerPickerToggled -> state.copy(
            isPickingPeer = intent.isOpen,
            query = "",
            peers = emptyList(),
            isSearchingPeers = false,
        )

        is ChatListIntent.UnreadChanged -> state.copy(totalUnread = intent.total)

        is ChatListIntent.ConnectionChanged -> state.copy(connection = intent.state)

        is ChatListIntent.Failed ->
            state.copy(isLoading = false, isRefreshing = false, isSearchingPeers = false, error = intent.message)

        ChatListIntent.ErrorCleared -> state.copy(error = null)
    }
}
