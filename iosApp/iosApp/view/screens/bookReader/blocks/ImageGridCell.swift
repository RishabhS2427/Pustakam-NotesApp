import SwiftUI
import shared

struct ImageGridCell: View {
    let media: NoteContentModel.MediaContent
    let overflow: Int
    var onTap: (NoteContentModel.MediaContent) -> Void = { _ in }

    var body: some View {
        ZStack {
            Color.black
            // same loader the editor's CardImageEditor uses — a file path needs fileURLWithPath
            AsyncImage(url: URL(fileURLWithPath: media.getMediaUrl())) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    Color.clear
                }
            }
            if overflow > 0 {
                Color.black
                Text("+\(overflow)").font(.title2).foregroundColor(.white)
            }
            // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
            VStack { Spacer(); MediaDownloadOverlay(media: media) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture { onTap(media) }
    }
}
