import SwiftUI
import shared

/// 🧩 DRY: chat has NO media views of its own. An attachment becomes a NoteContentModel.MediaContent
/// and the existing note blocks render it — the same ImageGridCell, VideoGridCell, AudioPlayView and
/// DocumentFileCardView the reader and editor already use. A fix to any of them fixes chat too.
struct ChatAttachmentsView: View {

    let message: ChatMessage
    var onOpenMedia: (NoteContentModel.MediaContent) -> Void = { _ in }
    var onOpenDocument: (NoteContentModel.MediaContent) -> Void = { _ in }

    private var medias: [NoteContentModel.MediaContent] { message.mediaContents() }

    var body: some View {
        if !medias.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
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
