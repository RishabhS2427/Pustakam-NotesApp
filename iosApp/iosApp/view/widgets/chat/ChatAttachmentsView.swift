import SwiftUI
import shared

/// 🧩 DRY: chat has NO media views of its own. An attachment becomes a NoteContentModel.MediaContent
/// and the existing note blocks render it — the same ImageGridCell, VideoGridCell, AudioPlayView and
/// DocumentFileCardView the reader and editor already use. A fix to any of them fixes chat too.
struct ChatAttachmentsView: View {

    let message: ChatMessage
    var onOpenMedia: (NoteContentModel.MediaContent) -> Void = { _ in }
    var onOpenDocument: (NoteContentModel.MediaContent) -> Void = { _ in }
    var onOpenNote: (String) -> Void = { _ in }

    private var medias: [NoteContentModel.MediaContent] { message.mediaContents() }

    private var notes: [ChatAttachment] { message.sharedNotes() }

    var body: some View {
        if !medias.isEmpty || !notes.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(notes.enumerated()), id: \.offset) { _, card in
                    SharedNoteCard(card: card, onOpen: onOpenNote)
                }
                ForEach(medias, id: \.id) { media in
                    switch media.type {
                    case .image, .gif:
                        ImageGridCell(media: media, overflow: 0, onTap: onOpenMedia)
                            .frame(height: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 10))

                    case .video:
                        VideoGridCell(media: media, overflow: 0, width: 240, height: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 10))

                    case .audio:
                        AudioPlayView(mediaContent: media)

                    default:
                        DocumentFileCardView(media: media, onOpen: { onOpenDocument(media) })
                    }
                }
            }
        }
    }
}

private struct SharedNoteCard: View {

    let card: ChatAttachment
    let onOpen: (String) -> Void

    @Environment(\.palette) private var palette

    var body: some View {
        Button {
            if let noteId = card.sharedNoteId() { onOpen(noteId) }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "doc.text").foregroundColor(palette.accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(card.title.isEmpty ? ChatShareSheet.shared.UNTITLED : card.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(Theme.Colors.text)
                        .lineLimit(1)
                    Text(ChatShareSheet.shared.OPEN_NOTE)
                        .font(.caption)
                        .foregroundColor(palette.accent)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 10).fill(Theme.Colors.background))
        }
        .buttonStyle(.plain)
    }
}
