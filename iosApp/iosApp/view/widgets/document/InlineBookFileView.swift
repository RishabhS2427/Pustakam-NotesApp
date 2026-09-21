import SwiftUI
import shared

// 🔧 19-Jul-2026: notebook palette — matches the reader's paper/leather look
private enum NotebookPalette {
    static let paper = Color(red: 0.98, green: 0.95, blue: 0.89)
    static let ink = Color(red: 0.24, green: 0.18, blue: 0.11)
    static let cover = Color(red: 0.29, green: 0.21, blue: 0.15)
    static let spiral = Color(red: 0.55, green: 0.52, blue: 0.47)
}

struct InlineBookFileView: View {
    let media: NoteContentModel.MediaContent
    var onOpenFull: () -> Void = {}
    var onDelete: () -> Void = {}
    var onSave: () -> Void = {}
    var onShare: () -> Void = {}
    var onPageChange: (Int32)-> Void = {_ in}

    @State private var pages: [BookPageItem] = []
    @State private var building = true
    @State private var currentIndex = 0
    @State private var startIndex = 0
    @State private var showActions = false
    @State private var hideToken = 0
    private func revealActions() {
        hideToken += 1
        let token = hideToken
        withAnimation(.easeInOut(duration: 0.25)) { showActions = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            guard token == hideToken else { return }
            withAnimation(.easeInOut(duration: 0.5)) { showActions = false }
        }
    }

    var body: some View {
        HStack(spacing: 0) {
            spiralBinding
            VStack(spacing: 0) {
                header
                Rectangle().fill(NotebookPalette.ink.opacity(0.15)).frame(height: 1)
                ZStack {
                    if building {
                        ProgressView().tint(NotebookPalette.cover)
                    } else if !pages.isEmpty {
                        BookPageCurlView(pages: pages, startIndex: startIndex) { index in
                            currentIndex = index
                            onPageChange(Int32(index))
                        }
                    }

                    if showActions && !pages.isEmpty { actionsOverlay }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
                // 🔧 19-Jul-2026: "<selected>/<total>" instead of dots — e.g. 2/20
                if !pages.isEmpty {
                    Text("\(currentIndex + 1)/\(pages.count)")
                        .font(.caption2.weight(.medium))
                        .foregroundColor(NotebookPalette.ink.opacity(0.65))
                        .padding(.vertical, 3)
                }

            // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
                MediaDownloadOverlay(media: media)
            }
            .background(NotebookPalette.paper)
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 0,
                                              bottomTrailingRadius: 12, topTrailingRadius: 12))
        }
        // 🔧 20-Jul-2026: size to the device — ~42% of screen height, clamped to a sane range
        .frame(height: min(max(UIScreen.main.bounds.height * 0.42, 260), 400))
        .padding(6)
        .background(RoundedRectangle(cornerRadius: 16).fill(NotebookPalette.cover))
        .onAppear(perform: buildPages)
        // rebuild when the row changes in the DB — mirrors Android's LaunchedEffect(media.id, media.updatedAt)
        .onChange(of: media.id) { _, _ in buildPages() }
        .onChange(of: media.updatedAt) { _, _ in buildPages() }
    }
    private var header: some View {
        HStack(spacing: 8) {
            Image(systemName: iconNameForContentType(media.type))
                .font(.system(size: 13)).foregroundColor(NotebookPalette.cover)
            Text(media.title.isEmpty ? "File" : media.title)
                .font(.system(.caption, design: .serif).weight(.semibold))
                .foregroundColor(NotebookPalette.ink).lineLimit(1)
            Spacer()
            Button(action: onOpenFull) {
                Image(systemName: "arrow.up.left.and.arrow.down.right")
                    .font(.system(size: 11)).foregroundColor(NotebookPalette.cover)
            }
            CardActionsButton(tint: NotebookPalette.cover, iconSize: 12, diameter: 22, chipColor: nil) {
                revealActions()
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture { onOpenFull() }
        .contextMenu {
            Button { onOpenFull() } label: { Label("Open full book", systemImage: "book") }
            Button { onSave() } label: { Label("Save to device", systemImage: "square.and.arrow.down") }
            Button(role: .destructive) { onDelete() } label: { Label("Delete", systemImage: "trash") }
        }
    }

    // 🔧 19-Jul-2026: binder rings column like the reference image
    private var spiralBinding: some View {
        VStack(spacing: 16) {
            ForEach(0..<9, id: \.self) { _ in
                Circle()
                    .strokeBorder(NotebookPalette.spiral, lineWidth: 2.5)
                    .frame(width: 13, height: 13)
            }
        }
        .frame(width: 26)
        .frame(maxHeight: .infinity)
    }

    private var actionsOverlay: some View {
        VStack {
            Spacer()
            ZStack(alignment: .bottom) {
                LinearGradient(
                    colors: [.clear, .black.opacity(0.05), .black.opacity(0.35), .black.opacity(0.55)],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(height: 90)
                HStack(spacing: 40) {
                    Button { onDelete() } label: {
                        Image(systemName: "trash.fill").font(.title2).foregroundColor(.red)
                    }
                    Button { onSave() } label: {   // download to device
                        Image(systemName: "square.and.arrow.down.fill").font(.title2).foregroundColor(.white)
                    }
                    Button { onShare() } label: {  // share sheet
                        Image(systemName: "square.and.arrow.up.fill").font(.title2).foregroundColor(.white)
                    }
                }
                .padding(.bottom, 16)
            }
            .frame(height: 70)
            .transition(.opacity)
        }
    }

    // 🔧 19-Jul-2026: pages built by the SHARED builder, off the main thread
    private func buildPages() {
        building = true
        DispatchQueue.global(qos: .userInitiated).async {
            let built = BookPagesBuilder.buildForContent(media)
            // 📖 25-Jul-2026: resume the inline preview at the last-read page (clamped to the count)
            let resume = media.hasReadingProgress()
                ? min(max(Int(media.progressPage), 0), max(built.count - 1, 0)) : 0
            DispatchQueue.main.async {
                pages = built
                startIndex = resume
                currentIndex = resume
                building = false
            }
        }
    }
}
