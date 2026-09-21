// 🔧 18-Jul-2026: NEW FEATURE (file import) — inline card for imported document/file content
//   (pdf/docx/epub/txt/md/other). Tap = open in the book reader; long-press = delete/save actions.
//   QuickLookPreview is also used by the book reader's file pages.

import SwiftUI
import QuickLook
import shared

// 🔧 18-Jul-2026: ContentType → SF Symbol for the card leading icon
func iconNameForContentType(_ type: ContentType) -> String {
    switch type {
    case .pdf: return "doc.richtext"
    case .docx: return "doc.text"
    case .epub: return "books.vertical"
    case .txt, .md: return "doc.plaintext"
    case .image: return "photo"
    case .gif: return "photo.stack"
    case .video: return "film"
    case .audio: return "waveform"
    default: return "doc"
    }
}

// 📏 20-Sep-2026: one size-wording rule, shared with the download bar and with Android
func readableFileSize(_ bytes: Int64) -> String {
    guard bytes > 0 else { return "" }
    return MediaSizeFormatter.shared.formatBytes(bytes: bytes)
}

struct DocumentFileCardView: View {
    let media: NoteContentModel.MediaContent
    var onOpen: () -> Void = {}
    var onDelete: () -> Void = {}
    var onSave: () -> Void = {}

    // 📖 23-Jul-2026: "<current>/<total>" once the document has been opened (Android inline-card
    //   parity). progressPage is 0-based on both platforms, so display is +1.
    private var readingProgressLabel: String {
        guard media.hasReadingProgress() else { return "" }
        return "\(Int(media.progressPage) + 1)/\(Int(media.totalPages))"
    }

    var body: some View {
        VStack(spacing: 0) {
            cardRow
            // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
            MediaDownloadOverlay(media: media)
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.gray.opacity(0.12)))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .onTapGesture { onOpen() }
        // 🔧 18-Jul-2026: same long-press action pattern as the media cards
        .contextMenu {
            Button { onOpen() } label: { Label("Open in book", systemImage: "book") }
            Button { onSave() } label: { Label("Save to device", systemImage: "square.and.arrow.down") }
            Button(role: .destructive) { onDelete() } label: { Label("Delete", systemImage: "trash") }
        }
    }

    private var cardRow: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Theme.Colors.secondary.opacity(0.15))
                    .frame(width: 48, height: 48)
                Image(systemName: iconNameForContentType(media.type))
                    .font(.system(size: 20))
                    .foregroundColor(Theme.Colors.secondary)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(media.title.isEmpty ? (media.localPath as NSString?)?.lastPathComponent ?? "File" : media.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                // 📖 23-Jul-2026: show the SAME reading position as the reader — both read
                //   MediaContent.progressPage/totalPages, so "3/4" here matches "3 / 4" in the
                //   reader. Documents never opened have no progress and show nothing extra.
                let subtitle = [media.type.name, readableFileSize(media.sizeBytes), readingProgressLabel]
                    .filter { !$0.isEmpty }.joined(separator: " · ")
                Text(subtitle).font(.caption).foregroundColor(.secondary)
            }
            Spacer()
            Image(systemName: "book").foregroundColor(Theme.Colors.secondary.opacity(0.7))
        }
        .padding(12)
    }
}

// 🔧 18-Jul-2026: QuickLook wrapper — system-quality preview for DOCX/EPUB/PDF/anything
struct QuickLookPreview: UIViewControllerRepresentable {
    let fileURL: URL

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: QLPreviewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(fileURL: fileURL) }

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let fileURL: URL
        init(fileURL: URL) { self.fileURL = fileURL }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            fileURL as NSURL
        }
    }
}
