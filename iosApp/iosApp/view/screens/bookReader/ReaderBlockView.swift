import SwiftUI
import shared

// 📖 01-Aug-2026: block -> widget. Pure dispatch; it never decides WHAT goes on a page, only how a
//   block already placed by the shared engine draws itself. Mirrors Android ReaderBlockContent.
struct ReaderBlockView: View {
    let block: ReaderBlock
    let policy: PageLayoutPolicy
    var documents: InlineDocumentState = .disabled
    var zoomEnabled: Bool = true
    var onOpenDocument: (NoteContentModel.MediaContent) -> Void = { _ in }
    var onOpenImage: (NoteContentModel.MediaContent) -> Void = { _ in }

    var body: some View {
        switch block {
        case let title as ReaderBlock.Title:
            TitleBlockView(block: title)
        case let paragraph as ReaderBlock.Paragraph:
            ParagraphBlockView(block: paragraph)
        case let rich as ReaderBlock.RichParagraph:
            RichParagraphBlockView(block: rich)
        case let images as ReaderBlock.ImageGrid:
            ImageGridBlockView(block: images, policy: policy, onTap: onOpenImage)
        case let videos as ReaderBlock.VideoGrid:
            VideoGridBlockView(block: videos, policy: policy,)
        case let audio as ReaderBlock.Audio:
            AudioBlockView(block: audio)
        case let document as ReaderBlock.Document:
            DocumentBlockView(block: document, documents: documents, onOpen: onOpenDocument)
        case let sheet as ReaderBlock.DocumentPage:
            DocumentPageBlockView(block: sheet, documents: documents, zoomEnabled: zoomEnabled)
        case let link as ReaderBlock.Link:
            LinkBlockView(block: link)
        case let location as ReaderBlock.Location:
            LocationBlockView(block: location)
        case let drawing as ReaderBlock.Drawing:
            DrawingBlockView(block: drawing, policy: policy)
        default:
            EmptyView()
        }
    }
}
