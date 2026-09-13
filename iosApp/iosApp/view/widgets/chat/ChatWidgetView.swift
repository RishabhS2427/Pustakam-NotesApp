import SwiftUI
import shared

private let loadOlderTriggerIndex = 2

/// 💬 THE reusable chat surface, iOS side — the twin of Android's ChatWidget.
///
/// Hand it a conversationId and it works anywhere: a sheet, a side panel beside a note, or the
/// full-screen chat screen, which is nothing more than this view with a title bar.
///
/// Media is deliberately NOT rendered here — ChatAttachmentsView hands attachments to the existing
/// note blocks, so an image in a chat looks and behaves exactly like an image in a note.
struct ChatWidgetView: View {

    let conversationId: String
    var showHeader: Bool = false
    var showComposer: Bool = true
    var emptyMessage: String = "No messages yet. Say something."
    var onOpenFullScreen: (() -> Void)?
    var onAttach: () -> Void = {}
    var onOpenMedia: (NoteContentModel.MediaContent) -> Void = { _ in }
    var onOpenDocument: (NoteContentModel.MediaContent) -> Void = { _ in }

    @StateObject private var viewModel = ChatViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.palette) private var palette
    @State private var draft = ""

    private var state: ChatState { viewModel.state }

    var body: some View {
        VStack(spacing: 0) {
            if showHeader { header; Divider() }
            if state.showsConnectionBanner { connectionBanner }

            messageList

            TypingIndicatorView(names: viewModel.typingNames())

            if let error = state.error { errorStrip(error) }

            if showComposer {
                Divider()
                ChatComposerView(
                    draft: $draft,
                    canSend: state.canSend,
                    attachments: state.pendingAttachments,
                    placeholder: state.isAssistantThread ? "Ask anything" : "Message",
                    onSend: viewModel.send,
                    onAttach: onAttach,
                    onRemoveAttachment: viewModel.removeAttachment
                )
            }
        }
        .background(Theme.Colors.background)
        .onAppear { viewModel.open(conversationId: conversationId) }
        .onDisappear { viewModel.close() }
        // The composer is a local @State so typing stays instant; the reducer still owns the draft
        .onChange(of: draft) { _, newValue in viewModel.onDraftChange(newValue) }
        .onChange(of: state.draft) { _, reduced in if reduced.isEmpty && !draft.isEmpty { draft = "" } }
        // Backgrounding drops the socket rather than holding a radio open for hours
        .onChange(of: scenePhase) { _, phase in viewModel.onForeground(phase == .active) }
    }

    // MARK: - Pieces

    private var header: some View {
        HStack(spacing: 10) {
            ChatAvatarView(
                label: state.title,
                avatarUrl: state.conversation?.avatarUrl(),
                isAssistant: state.isAssistantThread,
                size: 34
            )
            VStack(alignment: .leading, spacing: 1) {
                Text(state.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text(connectionSubtitle).font(.caption2).foregroundColor(Theme.Colors.text3)
            }
            Spacer()
            if let onOpenFullScreen {
                Button(action: onOpenFullScreen) {
                    Image(systemName: "arrow.up.left.and.arrow.down.right")
                        .foregroundColor(Theme.Colors.text2)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Theme.Colors.surfaceRaise)
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    if state.isLoadingOlder {
                        Text("Loading earlier messages…")
                            .font(.caption2).foregroundColor(Theme.Colors.text3).padding(10)
                    }

                    ForEach(Array(state.messages.enumerated()), id: \.element.id) { index, message in
                        // The date separator sits above the FIRST message of each day
                        if index == 0 ||
                            ChatTimeFormatter.dayLabel(state.messages[index - 1].createdAt)
                            != ChatTimeFormatter.dayLabel(message.createdAt) {
                            ChatDaySeparator(label: ChatTimeFormatter.dayLabel(message.createdAt))
                        }

                        ChatMessageBubbleView(
                            message: message,
                            isMine: state.isMine(message: message),
                            onDelete: viewModel.delete,
                            onOpenMedia: onOpenMedia,
                            onOpenDocument: onOpenDocument
                        )
                        .id(message.id)
                        // Reaching the top asks for the previous page; the ViewModel guards repeats
                        .onAppear { if index <= loadOlderTriggerIndex { viewModel.loadOlder() } }
                    }
                }
                .padding(.vertical, 8)
            }
            .overlay {
                if state.isLoading && state.messages.isEmpty {
                    LoadingUI()
                } else if state.isEmpty {
                    Text(emptyMessage)
                        .font(.body)
                        .foregroundColor(Theme.Colors.text3)
                        .multilineTextAlignment(.center)
                        .padding(32)
                }
            }
            // A new message scrolls the thread down, the way every chat behaves
            .onChange(of: state.messages.count) { _, _ in
                guard let last = state.messages.last else { return }
                withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
            }
        }
    }

    /// Honest about the socket. A chat that looks live while it is not is worse than one that says so.
    private var connectionBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "wifi.slash").font(.system(size: 11))
            Text(state.connection == .reconnecting
                 ? "Reconnecting…"
                 : "Offline — messages will send when you are back")
                .font(.caption2)
            Spacer()
        }
        .foregroundColor(Theme.Colors.onError)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(Theme.Colors.error.opacity(0.85))
    }

    private func errorStrip(_ message: String) -> some View {
        HStack {
            Text(message).font(.caption).foregroundColor(Theme.Colors.onError)
            Spacer()
            Button("Dismiss") { viewModel.clearError() }
                .font(.caption.weight(.semibold))
                .foregroundColor(Theme.Colors.onError)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Theme.Colors.error.opacity(0.85))
    }

    private var connectionSubtitle: String {
        switch state.connection {
        case .connected: return "Online"
        case .connecting: return "Connecting…"
        case .reconnecting: return "Reconnecting…"
        default: return "Offline"
        }
    }
}
