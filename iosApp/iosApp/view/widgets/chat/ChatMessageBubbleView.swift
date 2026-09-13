import SwiftUI
import shared

private let bubbleWidthFraction: CGFloat = 0.82

/// 💬 One message. Mine on the right in the accent colour, everyone else's on the left; the
/// assistant gets its own tint and a sparkle so an AI answer is never mistaken for a person's.
struct ChatMessageBubbleView: View {

    let message: ChatMessage
    let isMine: Bool
    var senderName: String = ""
    var showSenderName: Bool = false
    var onDelete: (ChatMessage) -> Void = { _ in }
    var onOpenMedia: (NoteContentModel.MediaContent) -> Void = { _ in }
    var onOpenDocument: (NoteContentModel.MediaContent) -> Void = { _ in }

    @Environment(\.palette) private var palette

    private var isAssistant: Bool { message.isFromAssistant() }

    private var bubbleColor: Color {
        if isMine { return palette.accent.opacity(0.18) }
        return isAssistant ? Theme.Colors.gold.opacity(0.18) : Theme.Colors.surface2
    }

    var body: some View {
        HStack {
            if isMine { Spacer(minLength: 24) }

            VStack(alignment: .leading, spacing: 4) {
                if showSenderName && !isMine && !senderName.isEmpty {
                    Text(senderName).font(.caption.weight(.semibold)).foregroundColor(palette.accent)
                }

                ChatAttachmentsView(
                    message: message,
                    onOpenMedia: onOpenMedia,
                    onOpenDocument: onOpenDocument
                )

                if !message.text.isEmpty {
                    Text(message.text).font(.body).foregroundColor(Theme.Colors.text)
                }

                // 🤖 an answer still being written shows dots rather than an empty bubble
                if message.isStreaming() && message.text.isEmpty {
                    TypingDotsView()
                }

                HStack(spacing: 4) {
                    Spacer(minLength: 0)
                    if isAssistant {
                        Image(systemName: "sparkles").font(.system(size: 9)).foregroundColor(Theme.Colors.text3)
                    }
                    Text(ChatTimeFormatter.clock(message.createdAt))
                        .font(.system(size: 10)).foregroundColor(Theme.Colors.text3)
                    // Only my own messages carry ticks — a tick on someone else's means nothing
                    if isMine { statusTick }
                }

                if message.aiState == .failed, let reason = message.aiError {
                    Text(reason).font(.system(size: 10)).foregroundColor(Theme.Colors.error)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: UIScreen.main.bounds.width * bubbleWidthFraction, alignment: .leading)
            .background(bubbleColor)
            .clipShape(BubbleShape(isMine: isMine))
            .contextMenu {
                if isMine {
                    Button(role: .destructive) { onDelete(message) } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
                Button { UIPasteboard.general.string = message.text } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
            }

            if !isMine { Spacer(minLength: 24) }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private var statusTick: some View {
        switch message.status {
        case .sending:
            Image(systemName: "clock").font(.system(size: 9)).foregroundColor(Theme.Colors.text3)
        case .sent:
            Image(systemName: "checkmark").font(.system(size: 9)).foregroundColor(Theme.Colors.text3)
        case .delivered:
            Image(systemName: "checkmark.circle").font(.system(size: 9)).foregroundColor(Theme.Colors.text3)
        case .read:
            Image(systemName: "checkmark.circle.fill").font(.system(size: 9)).foregroundColor(palette.accent)
        case .failed:
            Image(systemName: "exclamationmark.circle").font(.system(size: 9)).foregroundColor(Theme.Colors.error)
        default:
            EmptyView()
        }
    }
}

/// The tail sits on the sender's side, so who said what is readable at a glance.
private struct BubbleShape: Shape {
    let isMine: Bool

    func path(in rect: CGRect) -> Path {
        Path(
            UIBezierPath(
                roundedRect: rect,
                byRoundingCorners: isMine ? [.topLeft, .topRight, .bottomLeft] : [.topLeft, .topRight, .bottomRight],
                cornerRadii: CGSize(width: 16, height: 16)
            ).cgPath
        )
    }
}

/// A day separator, so a thread read weeks later still says when things were said.
struct ChatDaySeparator: View {
    let label: String

    var body: some View {
        Text(label)
            .font(.system(size: 10))
            .foregroundColor(Theme.Colors.text3)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Theme.Colors.surface2)
            .clipShape(Capsule())
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
    }
}
