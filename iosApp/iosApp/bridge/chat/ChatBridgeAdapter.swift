import Foundation
import shared

/// 💬 Owns the Kotlin ChatBridge and its Closeables. Same lifecycle rules as NotesBridgeAdapter:
/// observers are retained and closed on deinit; writes are deliberately NOT retained, because a
/// SwiftUI View struct being recreated must never cancel a message that is already on its way.
final class ChatBridgeAdapter {

    private let bridge = ChatBridge()
    private var closeables: [Closeable] = []

    deinit {
        closeables.forEach { $0.close() }
        bridge.dispose()
    }

    var currentUserId: String { bridge.currentUserId }

    // MARK: - Lifecycle

    func start() { bridge.start() }

    func stop() { bridge.stop() }

    func setAppForeground(_ isForeground: Bool) { bridge.setAppForeground(isForeground: isForeground) }

    func retryPending() { bridge.retryPending() }

    // MARK: - Reactive state

    func observeConversations(onChange: @escaping ([ChatConversation]) -> Void) {
        closeables.append(bridge.observeConversations(onChange: onChange))
    }

    func observeMessages(onChange: @escaping ([ChatMessage]) -> Void) {
        closeables.append(bridge.observeMessages(onChange: onChange))
    }

    func observeConnection(onChange: @escaping (ChatConnectionState) -> Void) {
        closeables.append(bridge.observeConnection(onChange: onChange))
    }

    func observeTotalUnread(onChange: @escaping (Int) -> Void) {
        closeables.append(bridge.observeTotalUnread { onChange(Int(truncating: $0)) })
    }

    func observeTyping(conversationId: String, onChange: @escaping ([String]) -> Void) {
        closeables.append(bridge.observeTyping(conversationId: conversationId, onChange: onChange))
    }

    // MARK: - Conversations

    func refresh(onState: @escaping (UiState<NSArray>) -> Void) {
        closeables.append(bridge.refresh(
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0 as NSArray?)) },
            onError:   { onState(.failure($0)) }
        ))
    }

    func openDirectConversation(participantId: String, onState: @escaping (UiState<ChatConversation>) -> Void) {
        _ = bridge.openDirectConversation(
            participantId: participantId,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        )
    }

    func openAssistantConversation(title: String?, onState: @escaping (UiState<ChatConversation>) -> Void) {
        _ = bridge.openAssistantConversation(
            title: title,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        )
    }

    func deleteConversation(conversationId: String, onState: @escaping (UiState<KotlinBoolean>) -> Void) {
        _ = bridge.deleteConversation(
            conversationId: conversationId,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        )
    }

    func peers(query: String?, onState: @escaping (UiState<NSArray>) -> Void) {
        closeables.append(bridge.peers(
            query: query,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0 as NSArray?)) },
            onError:   { onState(.failure($0)) }
        ))
    }

    // MARK: - One thread

    func openThread(conversationId: String) { bridge.openThread(conversationId: conversationId) }

    func closeThread() { bridge.closeThread() }

    func loadOlder(conversationId: String, before: Int64, onState: @escaping (UiState<NSArray>) -> Void) {
        closeables.append(bridge.loadOlder(
            conversationId: conversationId,
            before: before,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0 as NSArray?)) },
            onError:   { onState(.failure($0)) }
        ))
    }

    /// Not retained on purpose — see the note on deinit above.
    func send(
        conversationId: String,
        text: String,
        attachments: [ChatAttachment],
        onState: @escaping (UiState<ChatMessage>) -> Void
    ) {
        _ = bridge.send(
            conversationId: conversationId,
            text: text,
            attachments: attachments,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        )
    }

    func deleteMessage(conversationId: String, messageId: String, onState: @escaping (UiState<ChatMessage>) -> Void) {
        _ = bridge.deleteMessage(
            conversationId: conversationId,
            messageId: messageId,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        )
    }

    func markRead(conversationId: String) { bridge.markConversationRead(conversationId: conversationId) }

    func setTyping(conversationId: String, isTyping: Bool) {
        bridge.setTyping(conversationId: conversationId, isTyping: isTyping)
    }
}
