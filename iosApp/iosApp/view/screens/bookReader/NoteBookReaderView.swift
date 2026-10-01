import SwiftUI
import shared

// 📖 01-Aug-2026: WHOLE-NOTE reader. Pages come from the SHARED PageLayoutEngine — the same engine
//   Android calls — so both platforms produce identical page boundaries. This file only renders.
// 📖 15-Aug-2026: documents also open in place. The engine list never changes; sheets are spliced
//   on top of it by the shared ReaderDocumentExpander, exactly as on Android.
struct NoteBookReaderView: View {
    let noteId: String
    var startContentId: String? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(Router.self) private var router
    @State private var adapter = NotesBridgeAdapter()
    @State private var pages: [ReaderPage] = []
    @State private var isLoading = true
    @State private var currentIndex = 0
    @State private var startIndex = 0
    @State private var readerPrefs = ReaderPrefsAdapter()
    @State private var readingMode: ReadingMode = .page
    /// the MediaContent whose reading progress this book represents
    @State private var progressContentId: String? = nil
    @State private var previewImagePath: String? = nil
    @State private var previewMediaId: String? = nil

    // the engine list, never mutated — progress is always persisted against these indices
    @State private var basePages: [ReaderPage] = []
    @State private var expandedPages: ExpandedPages? = nil
    @State private var sources: [String: EmbeddedDocumentSource] = [:]
    @State private var expansions: [String: Int] = [:]
    @State private var busyIds: Set<String> = []
    @State private var unreadableIds: Set<String> = []

    // the ONE policy pages are generated against; the views read it back so what they draw always
    // matches the heights the engine reserved
    @State private var policy = PageLayoutPolicy.companion.standard()

    private var inlineDocuments: InlineDocumentState {
        InlineDocumentState(
            enabled: true,
            expandedIds: Set(expansions.keys),
            busyIds: busyIds,
            unreadableIds: unreadableIds,
            onToggle: { media in toggleDocument(media) },
            onLoadMore: { contentId in loadMoreDocumentPages(contentId) }
        )
    }

    var body: some View {
        ZStack {
            BookPalette.desk.ignoresSafeArea()
            if pages.isEmpty && isLoading {
                LoadingUI()
            } else if !pages.isEmpty {
                // 📖 both modes render the SAME pages — switching never rebuilds them
                switch readingMode {
                case .page:
                    ReaderPageCurlView(
                        pages: pages,
                        policy: policy,
                        startIndex: startIndex,
                        onPageChanged: { index in
                            currentIndex = index
                            saveProgress()
                        },
                        onOpenDocument: openDocument,
                        onOpenImage: openMedia,
                        inlineDocumentsEnabled: true,
                        inlineDocuments: inlineDocuments
                    )
                    .ignoresSafeArea(edges: .bottom)
                case .scroll:
                    ReaderScrollView(
                        pages: pages,
                        policy: policy,
                        startIndex: startIndex,
                        onPageChanged: { index in
                            currentIndex = index
                            saveProgress()
                        },
                        onOpenDocument: openDocument,
                        onOpenImage: openMedia,
                        inlineDocumentsEnabled: true,
                        inlineDocuments: inlineDocuments
                    )
                    .ignoresSafeArea(edges: .bottom)
                }
                VStack {
                    Spacer()
                    Text("\(currentIndex + 1) / \(pages.count)")
                        .font(.caption).foregroundColor(BookPalette.paper)
                        .padding(.horizontal, 12).padding(.vertical, 4)
                        .background(Capsule().fill(Color.black.opacity(0.45)))
                        .padding(.bottom, 10)
                }
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                BackButton(action: { dismiss() })
            }
            ToolbarItem(placement: .topBarTrailing) {
                if !pages.isEmpty {
                    Button {
                        let next = readingMode.toggled
                        readingMode = next
                        readerPrefs.setReadingMode(next)
                    } label: {
                        Image(systemName: readingMode.switchIcon)
                            .foregroundColor(BookPalette.paper)
                    }
                    .accessibilityLabel(Text(readingMode.switchLabel))
                }
            }
        }
        .onAppear {
            let bounds = UIScreen.main.bounds
            let resolved = PageLayoutPolicy.companion.forScreen(
                width: Float(bounds.width),
                height: Float(bounds.height)
            )
            policy = resolved
            loadNote(policy: resolved)
            readerPrefs.observeReadingMode { readingMode = $0 }
        }
        .onDisappear { saveProgress() }
        .fullScreenCover(isPresented: Binding(
            get: { previewImagePath != nil },
            set: { if !$0 { previewImagePath = nil } }
        )) {
            if let path = previewImagePath {
                ImagePreviewView(path: path, noteId: noteId, mediaId: previewMediaId) { previewImagePath = nil }
            }
        }
    }

    private func openMedia(_ media: NoteContentModel.MediaContent) {
        guard media.type == ContentType.image || media.type == ContentType.gif else { return }
        previewMediaId = media.id
        previewImagePath = media.getMediaUrl()
    }

    // 📖 15-Aug-2026: the card's arrow still opens the dedicated reader, as on Android
    private func openDocument(_ media: NoteContentModel.MediaContent) {
        router.navigate(to: .BookReader(bookId: media.id))
    }

    // MARK: - documents read in place

    private func toggleDocument(_ media: NoteContentModel.MediaContent) {
        let contentId = media.id
        if expansions[contentId] != nil {
            expansions.removeValue(forKey: contentId)
            publish(anchorOn: contentId)
            return
        }
        if let known = sources[contentId] {
            guard known.isReadable else { return }
            expansions[contentId] = Int(ReaderDocumentExpander.shared.firstWindow(pageCount: known.pageCount))
            publish(anchorOn: contentId)
            return
        }
        busyIds.insert(contentId)
        // counting sheets touches the file, so it happens once per document and off the main thread
        DispatchQueue.global(qos: .userInitiated).async {
            let probed = EmbeddedDocumentProbe.probe(media)
            DispatchQueue.main.async {
                self.sources[contentId] = probed
                self.busyIds.remove(contentId)
                if probed.isReadable {
                    self.expansions[contentId] = Int(
                        ReaderDocumentExpander.shared.firstWindow(pageCount: probed.pageCount)
                    )
                } else {
                    self.unreadableIds.insert(contentId)
                }
                self.publish(anchorOn: contentId)
            }
        }
    }

    private func loadMoreDocumentPages(_ contentId: String) {
        guard let source = sources[contentId], let loaded = expansions[contentId] else { return }
        let next = Int(ReaderDocumentExpander.shared.nextWindow(
            loadedCount: Int32(loaded), pageCount: source.pageCount
        ))
        guard next != loaded else { return }
        expansions[contentId] = next
        publish(anchorAt: currentIndex)
    }

    private func publish(anchorOn contentId: String? = nil, anchorAt: Int? = nil) {
        let rebuilt = ReaderDocumentExpander.shared.expand(
            basePages: basePages,
            sources: Array(sources.values),
            expansions: expansions.map { DocumentExpansion(contentId: $0.key, loadedCount: Int32($0.value)) },
            policy: policy
        )
        let list = rebuilt.pages
        let anchor: Int
        if let anchorAt {
            anchor = min(max(anchorAt, 0), max(list.count - 1, 0))
        } else if let contentId {
            anchor = cardIndex(list, contentId)
        } else {
            anchor = Int(rebuilt.displayIndexOf(baseIndex: Int32(currentIndex)))
        }
        expandedPages = rebuilt
        pages = list
        startIndex = anchor
        currentIndex = anchor
    }

    private func cardIndex(_ list: [ReaderPage], _ contentId: String) -> Int {
        list.firstIndex { page in
            !page.isDocumentSheet && page.blocks.contains { block in
                (block as? ReaderBlock.Document)?.item.id == contentId
            }
        } ?? 0
    }

    private func saveProgress() {
        guard let contentId = progressContentId, !basePages.isEmpty else { return }
        // a document sheet reports its card's page, so progress stays inside the engine list
        let base = Int(expandedPages?.baseIndexOf(displayIndex: Int32(currentIndex)) ?? Int32(currentIndex))
        let page = min(max(base, 0), basePages.count - 1)
        readerPrefs.saveProgress(contentId: contentId, page: page, totalPages: basePages.count)
    }

    private func loadNote(policy: PageLayoutPolicy) {
        adapter.readNote(noteId: noteId) { result in
            switch result {
            case .loading: if pages.isEmpty { isLoading = true }
            case .success(let note):
                guard let note else { isLoading = false; return }
                let wantedId = startContentId
                let allContents = (note.contents as? [NoteContentModel]) ?? []
                // 🐛 23-Jul-2026: page building is heavy, so it stays off the main thread
                DispatchQueue.global(qos: .userInitiated).async { [self] in
                    let built = PageLayoutEngine.shared.buildPages(note: note, policy: policy)
                    // an explicit startContentId (tapped card) wins over the stored resume page
                    let jump = Int(PageLayoutEngine.shared.pageIndexOf(pages: built, contentId: wantedId))
                    let media = allContents
                        .compactMap { $0 as? NoteContentModel.MediaContent }
                        .first { media in built.contains { $0.contains(contentId: media.id) } }
                    let saved: Int? = {
                        guard let m = media, m.hasReadingProgress(), !built.isEmpty else { return nil }
                        return min(max(Int(m.progressPage), 0), built.count - 1)
                    }()
                    let resolvedStart = jump >= 0 ? jump : (saved ?? 0)

                    DispatchQueue.main.async {
                        self.isLoading = false
                        self.basePages = built
                        self.pages = built
                        self.expandedPages = ExpandedPages(pages: built)
                        self.sources = [:]
                        self.expansions = [:]
                        self.busyIds = []
                        self.unreadableIds = []
                        self.progressContentId = media?.id
                        self.startIndex = resolvedStart
                        self.currentIndex = resolvedStart
                        BookWidgetStore.saveLastBook(noteId: note.id, title: note.title ?? "Untitled note")
                    }
                }
            case .failure(let error):
                // 🔧 19-Jul-2026: a local-miss can arrive BEFORE the data; never flash it
                print("NoteBookReader read failed (suppressed for UX): \(error.message)")
            case .idle: break
            }
        }
    }
}
