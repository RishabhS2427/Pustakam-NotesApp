package com.app.pustakam.feature.chat.domain.presentation

import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatParticipant

/** Everything that can change a chat screen, from the user or from the network. */
sealed interface ChatIntent {

    data class Open(val conversationId: String) : ChatIntent

    data object LoadStarted : ChatIntent

    data class ConversationLoaded(val conversation: ChatConversation) : ChatIntent

    data class MessagesLoaded(val messages: List<ChatMessage>) : ChatIntent

    data class OlderRequested(val before: Long) : ChatIntent

    data class OlderLoaded(val messages: List<ChatMessage>) : ChatIntent

    data class DraftChanged(val text: String) : ChatIntent

    data class AttachmentAdded(val attachment: ChatAttachment) : ChatIntent

    data class AttachmentRemoved(val attachment: ChatAttachment) : ChatIntent

    data object SendRequested : ChatIntent

    data object SendCompleted : ChatIntent

    data class TypingChanged(val userIds: Set<String>) : ChatIntent

    data class ConnectionChanged(val state: ChatConnectionState) : ChatIntent

    data class CurrentUserResolved(val userId: String) : ChatIntent

    data class Failed(val message: String) : ChatIntent

    data object ErrorCleared : ChatIntent
}

/** The inbox screen's intents. */
sealed interface ChatListIntent {

    data object Load : ChatListIntent

    data object RefreshRequested : ChatListIntent

    data class ConversationsLoaded(val conversations: List<ChatConversation>) : ChatListIntent

    data class PeersLoaded(val peers: List<ChatParticipant>) : ChatListIntent

    data class PeersSearching(val isSearching: Boolean) : ChatListIntent

    data class QueryChanged(val query: String) : ChatListIntent

    data class PeerPickerToggled(val isOpen: Boolean) : ChatListIntent

    data class UnreadChanged(val total: Int) : ChatListIntent

    data class ConnectionChanged(val state: ChatConnectionState) : ChatListIntent

    data class Failed(val message: String) : ChatListIntent

    data object ErrorCleared : ChatListIntent
}
