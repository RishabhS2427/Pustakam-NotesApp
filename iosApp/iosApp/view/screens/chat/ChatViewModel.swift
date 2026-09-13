import Foundation
import Combine
import shared

private let typingIdleSeconds: TimeInterval = 2.5

/// 💬 The thread, iOS side. Every state change goes through the SHARED ChatReducer, so this screen
/// and its Android twin cannot drift apart — and the behaviour is tested once, in commonTest.
final class ChatViewModel: ObservableObject {

    @Published private(set) var state: ChatState

    private let adapter: ChatBridgeAdapter
    private var typingTimer: Timer?
    private var openedConversationId: String?

    init(adapter: ChatBridgeAdapter = ChatBridgeAdapter()) {
        self.adapter = adapter
        self.state = ChatState(
            conversationId: nil,
            conversation: nil,
            messages: [],
            draft: "",
            pendingAttachments: [],
            typingUserIds: Set(),
            connection: .disconnected,
            currentUserId: adapter.currentUserId,
            isLoading: false,
            isSending: false,
            isLoadingOlder: false,
            hasMoreHistory: true,
            error: nil
        )

        // The message list, the connection and the typing map are owned by the repository —
        // this ViewModel only mirrors them into the reduced state (Android parity).
        adapter.observeMessages { [weak self] messages in
            self?.emit(ChatIntentMessagesLoaded(messages: messages))
        }
        adapter.observeConnection { [weak self] connection in
            self?.emit(ChatIntentConnectionChanged(state: connection))
        }
        adapter.observeConversations { [weak self] conversations in
            guard let self, let id = self.state.conversationId else { return }
            if let match = conversations.first(where: { $0.id == id }) {
                self.emit(ChatIntentConversationLoaded(conversation: match))
            }
        }
    }

    deinit { typingTimer?.invalidate() }

    // MARK: - Lifecycle

    /// Idempotent: re-entering the same thread must not reload it and lose the scroll position.
    func open(conversationId: String) {
        guard openedConversationId != conversationId else { return }
        openedConversationId = conversationId

        emit(ChatIntentOpen(conversationId: conversationId))
        emit(ChatIntentCurrentUserResolved(userId: adapter.currentUserId))
        adapter.start()
        adapter.openThread(conversationId: conversationId)

        adapter.observeTyping(conversationId: conversationId) { [weak self] ids in
            self?.emit(ChatIntentTypingChanged(userIds: Set(ids)))
        }
    }

    func close() {
        openedConversationId = nil
        stopTyping()
        adapter.closeThread()
    }

    func onForeground(_ isForeground: Bool) {
        adapter.setAppForeground(isForeground)
        if isForeground, let id = state.conversationId { adapter.markRead(conversationId: id) }
    }

    // MARK: - Composer

    func onDraftChange(_ text: String) {
        emit(ChatIntentDraftChanged(text: text))
        signalTyping(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false)
    }

    func addAttachment(_ attachment: ChatAttachment) {
        emit(ChatIntentAttachmentAdded(attachment: attachment))
    }

    func removeAttachment(_ attachment: ChatAttachment) {
        emit(ChatIntentAttachmentRemoved(attachment: attachment))
    }

    func send() {
        guard let conversationId = state.conversationId, state.canSend else { return }
        let text = state.draft
        let attachments = state.pendingAttachments

        // The reducer clears the composer NOW — the optimistic bubble is already on its way
        emit(ChatIntentSendRequested.shared)
        stopTyping()

        adapter.send(conversationId: conversationId, text: text, attachments: attachments) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success:
                self.emit(ChatIntentSendCompleted.shared)
            case .failure(let error):
                self.emit(ChatIntentFailed(message: error.message))
            case .loading, .idle:
                break
            }
        }
    }

    func loadOlder() {
        guard let conversationId = state.conversationId,
              !state.isLoadingOlder,
              state.hasMoreHistory,
              !state.messages.isEmpty else { return }

        let before = state.oldestMessageAt
        emit(ChatIntentOlderRequested(before: before))

        adapter.loadOlder(conversationId: conversationId, before: before) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let page):
                let messages = (page as? [ChatMessage]) ?? []
                self.emit(ChatIntentOlderLoaded(messages: messages))
            case .failure(let error):
                self.emit(ChatIntentFailed(message: error.message))
            case .loading, .idle:
                break
            }
        }
    }

    func delete(_ message: ChatMessage) {
        guard let conversationId = state.conversationId else { return }
        adapter.deleteMessage(conversationId: conversationId, messageId: message.id) { [weak self] result in
            if case .failure(let error) = result { self?.emit(ChatIntentFailed(message: error.message)) }
        }
    }

    func markRead() {
        guard let id = state.conversationId else { return }
        adapter.markRead(conversationId: id)
    }

    func clearError() { emit(ChatIntentErrorCleared.shared) }

    /// Typing ids mean nothing on screen; the conversation already carries the names.
    func typingNames() -> [String] {
        state.typingUserIds.map { id in
            state.conversation?.participantsInfo.first(where: { $0.id == id })?.displayName() ?? "Someone"
        }
    }

    // MARK: - Typing

    /// Debounced into "started" and "stopped after a pause" — a frame per keystroke would put one
    /// websocket message on the wire per letter.
    private func signalTyping(_ isTyping: Bool) {
        guard let conversationId = state.conversationId else { return }
        typingTimer?.invalidate()

        guard isTyping else {
            adapter.setTyping(conversationId: conversationId, isTyping: false)
            return
        }
        adapter.setTyping(conversationId: conversationId, isTyping: true)
        typingTimer = Timer.scheduledTimer(withTimeInterval: typingIdleSeconds, repeats: false) { [weak self] _ in
            self?.adapter.setTyping(conversationId: conversationId, isTyping: false)
        }
    }

    private func stopTyping() {
        typingTimer?.invalidate()
        guard let conversationId = state.conversationId else { return }
        adapter.setTyping(conversationId: conversationId, isTyping: false)
    }

    // Bridge callbacks already arrive on the main thread; this keeps SwiftUI happy either way
    private func emit(_ intent: ChatIntent) {
        let next = ChatReducer.shared.reduce(state: state, intent: intent)
        if Thread.isMainThread { state = next } else { DispatchQueue.main.async { self.state = next } }
    }
}
