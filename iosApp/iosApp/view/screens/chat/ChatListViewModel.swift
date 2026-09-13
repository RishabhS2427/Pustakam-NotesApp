import Foundation
import Combine
import shared

private let peerSearchDebounceSeconds: TimeInterval = 0.4

/// 💬 The inbox, iOS side. Same shared reducer as Android's ChatListViewModel.
final class ChatListViewModel: ObservableObject {

    @Published private(set) var state = ChatListState(
        conversations: [], peers: [], query: "", connection: .disconnected,
        totalUnread: 0, isLoading: false, isRefreshing: false, isPickingPeer: false,
        isSearchingPeers: false, error: nil
    )

    /// Set when a new conversation opens, so the view can navigate into it exactly once.
    @Published var openedConversationId: String?

    private let adapter: ChatBridgeAdapter
    private var hasLoaded = false
    private var peerSearchWork: DispatchWorkItem?

    init(adapter: ChatBridgeAdapter = ChatBridgeAdapter()) {
        self.adapter = adapter

        adapter.observeConversations { [weak self] conversations in
            self?.emit(ChatListIntentConversationsLoaded(conversations: conversations))
        }
        adapter.observeTotalUnread { [weak self] total in
            self?.emit(ChatListIntentUnreadChanged(total: Int32(total)))
        }
        adapter.observeConnection { [weak self] connection in
            self?.emit(ChatListIntentConnectionChanged(state: connection))
        }
    }

    /// One-time load, like the notes list. Live updates arrive on the flows above.
    func load() {
        adapter.start()
        guard !hasLoaded else { return }
        hasLoaded = true
        emit(ChatListIntentLoad.shared)
        fetchConversations()
    }

    func onForeground(_ isForeground: Bool) { adapter.setAppForeground(isForeground) }

    func refresh() {
        emit(ChatListIntentRefreshRequested.shared)
        fetchConversations()
    }

    /// 🆔 31-Aug-2026 — the query now drives a SERVER search by username prefix, debounced, instead
    /// of filtering a downloaded directory of everyone. A blank query returns nothing by design:
    /// there is no browsable list of users any more.
    func onQueryChange(_ query: String) {
        peerSearchWork?.cancel()
        emit(ChatListIntentQueryChanged(query: query))

        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            emit(ChatListIntentPeersLoaded(peers: []))
            return
        }
        emit(ChatListIntentPeersSearching(isSearching: true))

        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.adapter.peers(query: query) { [weak self] result in
                guard let self else { return }
                switch result {
                case .success(let list):
                    self.emit(ChatListIntentPeersLoaded(peers: (list as? [ChatParticipant]) ?? []))
                case .failure(let error):
                    self.emit(ChatListIntentFailed(message: error.message))
                case .loading, .idle:
                    break
                }
            }
        }
        peerSearchWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + peerSearchDebounceSeconds, execute: work)
    }

    func openPeerPicker() { emit(ChatListIntentPeerPickerToggled(isOpen: true)) }

    func closePeerPicker() {
        peerSearchWork?.cancel()
        emit(ChatListIntentPeerPickerToggled(isOpen: false))
    }

    func startDirectChat(participantId: String) {
        adapter.openDirectConversation(participantId: participantId) { [weak self] result in
            self?.handleOpened(result)
        }
    }

    /// 🤖 a fresh thread with the assistant. Several are allowed — they never collide server-side.
    func startAssistantChat() {
        adapter.openAssistantConversation(title: nil) { [weak self] result in
            self?.handleOpened(result)
        }
    }

    func delete(conversationId: String) {
        adapter.deleteConversation(conversationId: conversationId) { [weak self] result in
            if case .failure(let error) = result { self?.emit(ChatListIntentFailed(message: error.message)) }
        }
    }

    func clearError() { emit(ChatListIntentErrorCleared.shared) }

    private func handleOpened(_ result: UiState<ChatConversation>) {
        switch result {
        case .success(let conversation):
            guard let conversation else { return }
            emit(ChatListIntentPeerPickerToggled(isOpen: false))
            openedConversationId = conversation.id
        case .failure(let error):
            emit(ChatListIntentFailed(message: error.message))
        case .loading, .idle:
            break
        }
    }

    private func fetchConversations() {
        adapter.refresh { [weak self] result in
            guard let self else { return }
            switch result {
            // The list itself arrives through the conversations flow — a single source of truth
            case .success:
                self.emit(ChatListIntentConversationsLoaded(conversations: self.state.conversations))
            case .failure(let error):
                self.emit(ChatListIntentFailed(message: error.message))
            case .loading, .idle:
                break
            }
        }
    }

    private func emit(_ intent: ChatListIntent) {
        let next = ChatReducer.shared.reduceList(state: state, intent: intent)
        if Thread.isMainThread { state = next } else { DispatchQueue.main.async { self.state = next } }
    }
}
