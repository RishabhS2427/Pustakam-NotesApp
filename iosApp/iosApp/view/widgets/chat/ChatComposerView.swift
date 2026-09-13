import SwiftUI
import shared

/// 💬 The message box. Send is disabled rather than hidden when there is nothing to send, so the
/// button never moves under the user's thumb.
struct ChatComposerView: View {

    @Binding var draft: String
    let canSend: Bool
    var attachments: [ChatAttachment] = []
    var placeholder: String = "Message"
    var showAttachButton: Bool = true
    var onSend: () -> Void = {}
    var onAttach: () -> Void = {}
    var onRemoveAttachment: (ChatAttachment) -> Void = { _ in }

    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            if !attachments.isEmpty { attachmentStrip }

            HStack(alignment: .bottom, spacing: 8) {
                if showAttachButton {
                    Button(action: onAttach) {
                        Image(systemName: "paperclip")
                            .font(.system(size: 18))
                            .foregroundColor(Theme.Colors.text2)
                    }
                }

                TextField(placeholder, text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(Theme.Colors.surface2)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                Button(action: onSend) {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(canSend ? Theme.Colors.ivory : Theme.Colors.text3)
                        .frame(width: 40, height: 40)
                        .background(canSend ? palette.accent : Theme.Colors.surface2)
                        .clipShape(Circle())
                }
                .disabled(!canSend)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Theme.Colors.surfaceRaise)
    }

    /// What is queued to go with the next message, each removable before it is sent.
    private var attachmentStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Array(attachments.enumerated()), id: \.offset) { _, attachment in
                    HStack(spacing: 4) {
                        Text(attachment.title.isEmpty ? attachment.contentType.name : attachment.title)
                            .font(.caption)
                            .lineLimit(1)
                            .foregroundColor(Theme.Colors.text2)
                        Button { onRemoveAttachment(attachment) } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 13))
                                .foregroundColor(Theme.Colors.text3)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Theme.Colors.surface2)
                    .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 4)
        }
    }
}
