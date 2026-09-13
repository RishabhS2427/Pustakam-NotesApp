import SwiftUI
import shared

private let unreadCap = 99

/// One row of the inbox: who, the last thing said, when, and how much is unread.
struct ChatConversationRowView: View {

    let conversation: ChatConversation
    var isTyping: Bool = false
    var onTap: () -> Void = {}

    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            ChatAvatarView(
                label: conversation.displayTitle(),
                avatarUrl: conversation.avatarUrl(),
                isAssistant: conversation.isAssistant()
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(conversation.displayTitle())
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.Colors.text)
                    .lineLimit(1)

                // "typing…" replaces the preview rather than sitting beside it — one line, no jump
                if isTyping {
                    Text("typing…").font(.caption).foregroundColor(palette.accent)
                } else {
                    Text(conversation.lastMessagePreview.isEmpty ? "No messages yet" : conversation.lastMessagePreview)
                        .font(.caption)
                        .foregroundColor(Theme.Colors.text3)
                        .lineLimit(1)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                Text(ChatTimeFormatter.inboxStamp(conversation.lastMessageAt))
                    .font(.system(size: 10))
                    .foregroundColor(Theme.Colors.text3)
                if conversation.unreadCount > 0 { UnreadBadgeView(count: Int(conversation.unreadCount)) }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }
}

/// A picked-from list of people to start a new chat with.
struct ChatPeerRowView: View {
    let participant: ChatParticipant
    var onTap: () -> Void = {}

    var body: some View {
        HStack(spacing: 12) {
            ChatAvatarView(label: participant.displayName(), avatarUrl: participant.avatarUrl)
            VStack(alignment: .leading, spacing: 2) {
                Text(participant.displayName()).font(.subheadline.weight(.semibold))
                // 🆔 31-Aug-2026 — this printed the peer's EMAIL ADDRESS. It shows the handle now.
                if !participant.handle().isEmpty {
                    Text(participant.handle()).font(.caption).foregroundColor(Theme.Colors.text3).lineLimit(1)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }
}

/// The picture, or the first letter when there is none. Never an empty grey circle.
struct ChatAvatarView: View {
    let label: String
    var avatarUrl: String?
    var isAssistant: Bool = false
    var size: CGFloat = 46

    var body: some View {
        ZStack {
            Circle().fill(isAssistant ? Theme.Colors.gold.opacity(0.25) : Theme.Colors.sand.opacity(0.4))

            if isAssistant {
                Image(systemName: "sparkles")
                    .font(.system(size: size * 0.4))
                    .foregroundColor(Theme.Colors.copper)
            } else if let resolved = avatarUrl.absoluteMediaUrl, let url = URL(string: resolved) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        initialText
                    }
                }
                .clipShape(Circle())
            } else {
                initialText
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var initialText: some View {
        Text(label.trimmingCharacters(in: .whitespaces).prefix(1).uppercased())
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundColor(Theme.Colors.copper)
    }
}

struct UnreadBadgeView: View {
    let count: Int
    @Environment(\.palette) private var palette

    var body: some View {
        Text(count > unreadCap ? "\(unreadCap)+" : "\(count)")
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(Theme.Colors.ivory)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(palette.accent)
            .clipShape(Capsule())
    }
}
