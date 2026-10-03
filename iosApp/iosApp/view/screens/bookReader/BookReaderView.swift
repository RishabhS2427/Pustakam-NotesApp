
import SwiftUI
import PDFKit
import AVKit
import WidgetKit
import shared

// MARK: - Page model (Android BookPage parity)

enum BookPageItem: Identifiable {
    case cover(title: String, subtitle: String)
    case text(body: String, chunk: Int, chunkCount: Int, sourceId: String?)
    case image(path: String, title: String, sourceId: String?)
    case pdf(path: String, pageIndex: Int, pageCount: Int, title: String, sourceId: String?)
    case media(NoteContentModel.MediaContent)
    case docFile(NoteContentModel.MediaContent)
    case link(url: String, sourceId: String?)
    case location(lat: Double, lon: Double, address: String?, sourceId: String?)

    var id: String {
        switch self {
        case .cover: return "cover"
        case .text(_, let chunk, _, let s): return "text-\(s ?? "")-\(chunk)"
        case .image(let p, _, let s): return "img-\(s ?? p)"
        case .pdf(let p, let i, _, _, let s): return "pdf-\(s ?? p)-\(i)"
        case .media(let m): return "media-\(m.id)"
        case .docFile(let m): return "doc-\(m.id)"
        case .link(let u, let s): return "link-\(s ?? u)"
        case .location(let la, let lo, _, let s): return "loc-\(s ?? "\(la),\(lo)")"
        }
    }

    var sourceContentId: String? {
        switch self {
        case .cover: return nil
        case .text(_, _, _, let s), .image(_, _, let s), .pdf(_, _, _, _, let s),
             .link(_, let s), .location(_, _, _, let s): return s
        case .media(let m), .docFile(let m): return m.id
        }
    }
}

enum ReadingMode: String {
    case page, scroll

    var toggled: ReadingMode { self == .page ? .scroll : .page }

    // toolbar affordance shows what you'll GET, matching Android's toggle
    var switchIcon: String { self == .page ? "doc.plaintext" : "book" }
    var switchLabel: String { self == .page ? "Switch to scrolling" : "Switch to page curl" }

    static func from(_ raw: String?) -> ReadingMode { ReadingMode(rawValue: raw ?? "") ?? .page }
}

enum BookPalette {
    static let paper = Color(red: 0.98, green: 0.95, blue: 0.89)
    static let ink = Color(red: 0.24, green: 0.18, blue: 0.11)
    static let cover = Color(red: 0.36, green: 0.25, blue: 0.20)
    static let desk = Color(red: 0.14, green: 0.11, blue: 0.08)
}

// MARK: - Pages builder

enum BookPagesBuilder {

    private static let charsPerPage = Int(BookPaginator.shared.charsPerPage())
    private static let maxTextFileBytes = Int(BookPaginator.shared.maxTextFileBytes())

 
    private static func resolved(_ path: String?) -> String? {
        guard let path, !path.isEmpty else { return nil }
        return LocalFilePathResolver_iosKt.resolveLocalFilePath(path: path) ?? path
    }


    static func readablePdfPath(_ path: String?) -> String? {
        guard let path = resolved(path), !path.isEmpty else { return nil }
        let fm = FileManager.default
        var isDirectory: ObjCBool = false
        guard fm.fileExists(atPath: path, isDirectory: &isDirectory), !isDirectory.boolValue,
              fm.isReadableFile(atPath: path) else { return nil }
        let attributes = try? fm.attributesOfItem(atPath: path)
        let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        guard size > 0 else { return nil }
        guard let handle = FileHandle(forReadingAtPath: path) else { return nil }
        defer { try? handle.close() }
        let header = handle.readData(ofLength: 5)
        guard header.count == 5, [UInt8](header) == Array("%PDF-".utf8) else { return nil }
        return path
    }

    static func build(note: Note) -> [BookPageItem] {
        var pages: [BookPageItem] = []
        let contents = (note.contents as? [NoteContentModel] ?? []).sorted { $0.position < $1.position }
        let title = (note.title?.isEmpty == false ? note.title! : "Untitled note")
        pages.append(.cover(title: title, subtitle: "\(contents.count) entries"))
        // 🔧 19-Jul-2026: DRY — whole-note book delegates to the single-content builder
        //   (Self. avoids shadowing by the local `pages` array)
        for content in contents { pages.append(contentsOf: Self.pages(for: content)) }
        return pages
    }

    static func buildForContent(_ content: NoteContentModel) -> [BookPageItem] { pages(for: content) }

    // 🔧 19-Jul-2026: ONE content → its pages; the shared core every builder calls (DRY)
    static func pages(for content: NoteContentModel) -> [BookPageItem] {
        switch content {
        case let text as NoteContentModel.TextContent:
            return paginate(text.text, sourceId: text.id)

        case let media as NoteContentModel.MediaContent:
            switch media.type {
            case .image, .gif:
                return [.image(path: resolved(media.localPath) ?? media.url,
                               title: media.title, sourceId: media.id)]
            case .video, .audio:
                return [.media(media)]
            case .pdf:
                return pdfSheets(media)
            case .txt, .md:
                return textFilePages(media)
            default:
                return [.docFile(media)]   // docx / epub / other → QuickLook page
            }

        case let link as NoteContentModel.Link:
            return [.link(url: link.url, sourceId: link.id)]

        case let loc as NoteContentModel.Location:
            return [.location(lat: loc.latitude, lon: loc.longitude,
                              address: loc.address, sourceId: loc.id)]
        default: return []
        }
    }

    // 📖 15-Aug-2026: the chunking itself, shared with the inline document probe so a txt/md file
    //   paginates identically whether it is read inline or in this reader
    static func textChunks(_ text: String) -> [String] {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        var chunks: [String] = []
        var remaining = Substring(text)
        while remaining.count > charsPerPage {
            let window = remaining.prefix(charsPerPage)
            let cutIndex = window.lastIndex(of: "\n").flatMap { idx -> Substring.Index? in
                window.distance(from: window.startIndex, to: idx) > charsPerPage / 2 ? idx : nil
            } ?? window.lastIndex(of: " ").flatMap { idx -> Substring.Index? in
                window.distance(from: window.startIndex, to: idx) > charsPerPage / 2 ? idx : nil
            } ?? window.endIndex
            chunks.append(String(remaining[remaining.startIndex..<cutIndex]))
            remaining = remaining[cutIndex...].drop { $0 == "\n" || $0 == " " }
        }
        if !remaining.isEmpty { chunks.append(String(remaining)) }
        return chunks
    }

    private static func paginate(_ text: String, sourceId: String?) -> [BookPageItem] {
        let chunks = textChunks(text)
        return chunks.enumerated().map {
            .text(body: $1, chunk: $0 + 1, chunkCount: chunks.count, sourceId: sourceId)
        }
    }

    private static func pdfSheets(_ media: NoteContentModel.MediaContent) -> [BookPageItem] {
        guard let path = readablePdfPath(media.localPath) else {
            // 🐛 23-Jul-2026: leaves a breadcrumb naming the file we refused to open
            CrashBreadcrumb.rejectedUnreadablePdf(path: media.localPath)
            return [.docFile(media)]
        }
        guard let document = PDFDocument(url: URL(fileURLWithPath: path)),
              document.pageCount > 0 else {
            CrashBreadcrumb.rejectedUnreadablePdf(path: path)
            return [.docFile(media)]
        }
        CrashBreadcrumb.openingDocument(contentId: media.id, path: path, pageCount: Int(document.pageCount))
        return (0..<document.pageCount).map {
            .pdf(path: path, pageIndex: $0, pageCount: document.pageCount, title: media.title, sourceId: media.id)
        }
    }

    // 🔧 18-Jul-2026: txt/md file → paginated text pages (md shown as plain text v1)
    // 📖 15-Aug-2026: file read + byte cap, shared with the inline document probe
    static func cappedText(_ media: NoteContentModel.MediaContent) -> String? {
        guard let path = resolved(media.localPath),
              let data = FileManager.default.contents(atPath: path) else { return nil }
        let capped = data.count > maxTextFileBytes ? data.prefix(maxTextFileBytes) : data[...]
        return String(data: Data(capped), encoding: .utf8)
    }

    private static func textFilePages(_ media: NoteContentModel.MediaContent) -> [BookPageItem] {
        guard let text = cappedText(media) else { return [.docFile(media)] }
        let pages = paginate(text, sourceId: media.id)
        return pages.isEmpty ? [.docFile(media)] : pages
    }
}

// MARK: - Screen

struct BookReaderView: View {
    /// the MediaContent id of the document to read
    let bookId: String

    @Environment(\.dismiss) private var dismiss
    @State private var adapter = NotesBridgeAdapter()
    @State private var pages: [BookPageItem] = []
    @State private var isLoading = true
    @State private var currentIndex = 0
    @State private var readerPrefs = ReaderPrefsAdapter()
    @State private var readingMode: ReadingMode = .page
    /// the MediaContent whose reading progress this book represents
    @State private var progressContentId: String? = nil
    @StateObject private var annotations = BookReaderAnnotations()

    var body: some View {
        ZStack {
            BookPalette.desk.ignoresSafeArea()

            if pages.isEmpty && isLoading {
                LoadingUI()
            } else if !pages.isEmpty {

                switch readingMode {
                case .page:

                    BookPageCurlView(pages: pages, startIndex: currentIndex, annotations: annotations) { index in
                        currentIndex = index

                        saveProgress()
                    }
                    .ignoresSafeArea(edges: .bottom)
                case .scroll:

                    if let pdfPath = singlePdfPath {
                        NativePdfScrollView(path: pdfPath, startPageIndex: currentIndex, ink: annotations.pdfInk) { index in
                            currentIndex = index
                            saveProgress()
                        }
                        .overlay { pdfInk }
                        .ignoresSafeArea(edges: .bottom)
                    } else {
                        BookScrollReader(
                            pages: pages,
                            startIndex: currentIndex,
                            annotations: annotations,
                            zoomEnabled: !annotations.zoomLocked
                        ) { index in
                            currentIndex = index
                            saveProgress()   // 📖 same for scrolling
                        }
                        .ignoresSafeArea(edges: .bottom)
                    }
                }
                if !annotations.annotating {
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
            annotationChrome
        }
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                BackButton(action: {
                    if annotations.annotating {
                        annotations.drawing.stop()
                    } else {
                        dismiss()
                    }
                })
            }
            ToolbarItem(placement: .topBarTrailing) {
                if annotations.annotating, let session = annotations.drawing.active() {
                    DrawingHistoryButtons(session: session, tint: BookPalette.paper)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                if !pages.isEmpty && annotations.writable {
                    Button {
                        annotations.drawing.toggleOverlay()
                    } label: {
                        Image(systemName: DrawingIcons.draw)
                            .foregroundColor(annotations.annotating ? Theme.Colors.primary : BookPalette.paper)
                    }
                    .accessibilityLabel(Text("Write on document"))
                }
            }
            // 📖 23-Jul-2026: reading-mode toggle, right where it's used (mirrored in Settings)
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
            loadNote()

            readerPrefs.observeReadingMode { readingMode = $0 }
        }

        .onDisappear {
            saveProgress()
            annotations.flushAnnotation()
        }
    }

    @ViewBuilder
    private var pdfInk: some View {
        if let session = annotations.drawing.overlay {
            DrawingCanvas(session: session, anchors: annotations.pdfInk.anchors, input: annotations.annotating)
        }
    }

    @ViewBuilder
    private var annotationChrome: some View {
        if annotations.annotating, let session = annotations.drawing.active() {
            DrawingChrome(session: session, onDone: { annotations.drawing.stop() })
        }
    }


    private var singlePdfPath: String? {
        guard !pages.isEmpty else { return nil }
        var path: String? = nil
        for page in pages {
            guard case .pdf(let p, _, _, _, _) = page else { return nil }
            if path == nil { path = p } else if path != p { return nil }
        }
        return path
    }

    private func saveProgress() {
        guard let contentId = progressContentId, !pages.isEmpty else { return }
        let page = min(max(currentIndex, 0), pages.count - 1)
        readerPrefs.saveProgress(contentId: contentId, page: page, totalPages: pages.count)
    }



    private func loadNote() {
        adapter.readContent(contentId: bookId) { result in
            switch result {
            // 🔧 19-Jul-2026: FIX — a late Loading emission must not bring the loader back
            case .loading: if pages.isEmpty { isLoading = true }
            case .success(let content):
                guard let media = content as? NoteContentModel.MediaContent else {
                    isLoading = false
                    return
                }
                DispatchQueue.global(qos: .userInitiated).async {
                    let built = BookPagesBuilder.buildForContent(media)
                    // a document always resumes where it was left — there is nothing to jump to
                    let resolvedStart: Int = {
                        guard media.hasReadingProgress(), !built.isEmpty else { return 0 }
                        return min(max(Int(media.progressPage), 0), built.count - 1)
                    }()

                    DispatchQueue.main.async {
                        self.isLoading = false
                        self.pages = built
                        self.progressContentId = media.id
                        self.currentIndex = resolvedStart
                        self.annotations.followNote(noteId: media.noteId, documentId: media.id)
                        // 🔧 18-Jul-2026: remember this book for the home-screen widget
                        BookWidgetStore.saveLastBook(noteId: media.noteId,
                                                     title: media.title.isEmpty ? "Untitled note" : media.title)
                    }
                }
            case .failure(let error):
                print("BookReader read failed (suppressed for UX): \(error.message)")
            case .idle: break
            }
        }
    }
}

// MARK: - Continuous scroll reader
struct BookScrollReader: UIViewRepresentable {
    let pages: [BookPageItem]
    let startIndex: Int
    var annotations: BookReaderAnnotations? = nil
    var zoomEnabled: Bool = true
    let onPageChanged: (Int) -> Void

    private static let pageHeight: CGFloat = 560
    private static let spacing: CGFloat = 12
    private static let maxZoom: CGFloat = 5
    private static let doubleTapZoom: CGFloat = 2.5

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = ResumeAwareScrollView()
        scrollView.onLayout = { [weak coordinator = context.coordinator] view in
            coordinator?.applyPendingStartIfReady(view)
        }
        scrollView.delegate = context.coordinator
        scrollView.backgroundColor = .clear
        scrollView.minimumZoomScale = 1
        scrollView.maximumZoomScale = Self.maxZoom
        // pinch/pan/scroll all come from UIScrollView itself — no custom gesture maths
        scrollView.bouncesZoom = true
        scrollView.showsVerticalScrollIndicator = true

        let host = UIHostingController(rootView: content)
        host.view.backgroundColor = .clear
        scrollView.addSubview(host.view)
        context.coordinator.hostingController = host
        context.coordinator.contentView = host.view
        context.coordinator.pageCount = pages.count
        context.coordinator.contentSignature = pages.count

        // double-tap toggles 1x ↔ 2.5x, centred on the tap
        let doubleTap = UITapGestureRecognizer(target: context.coordinator,
                                               action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        scrollView.addGestureRecognizer(doubleTap)

        layout(scrollView, coordinator: context.coordinator)

        context.coordinator.pendingStartIndex = min(max(startIndex, 0), max(pages.count - 1, 0))
        context.coordinator.applyPendingStartIfReady(scrollView)
        return scrollView
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        context.coordinator.onPageChanged = onPageChanged
        context.coordinator.pageStride = Self.pageHeight + Self.spacing
        context.coordinator.pageCount = pages.count

        let signature = pages.count
        if context.coordinator.contentSignature != signature {
            context.coordinator.contentSignature = signature
            context.coordinator.hostingController?.rootView = content
            layout(scrollView, coordinator: context.coordinator)

            context.coordinator.pendingStartIndex = min(max(startIndex, 0), max(pages.count - 1, 0))
        }

        context.coordinator.applyPendingStartIfReady(scrollView)
        scrollView.pinchGestureRecognizer?.isEnabled = zoomEnabled
    }

    private var content: AnyView {
        AnyView(LazyVStack(spacing: Self.spacing) {
            ForEach(Array(pages.enumerated()), id: \.element.id) { index, page in
                pageFace(page, index: index)
                    .frame(height: Self.pageHeight)
            }
        }
        .padding(.vertical, Self.spacing))
    }

    @ViewBuilder
    private func pageFace(_ page: BookPageItem, index: Int) -> some View {
        if let annotations {
            AnnotatedBookPage(page: page, index: index, allowPageZoom: false, annotations: annotations)
        } else {
            BookPageContentView(page: page, allowPageZoom: false)
        }
    }

    private func layout(_ scrollView: UIScrollView, coordinator: Coordinator) {
        guard let contentView = coordinator.contentView else { return }
        let width = scrollView.bounds.width > 0 ? scrollView.bounds.width : UIScreen.main.bounds.width
        let height = CGFloat(pages.count) * (Self.pageHeight + Self.spacing) + Self.spacing
        contentView.frame = CGRect(x: 0, y: 0, width: width, height: height)
        scrollView.contentSize = CGSize(width: width, height: height)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onPageChanged: onPageChanged, pageStride: Self.pageHeight + Self.spacing,
                    doubleTapZoom: Self.doubleTapZoom)
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var hostingController: UIHostingController<AnyView>?
        var contentView: UIView?
        var onPageChanged: (Int) -> Void
        var pageStride: CGFloat
        let doubleTapZoom: CGFloat
        var pageCount = 0
        var contentSignature = -1
        var pendingStartIndex: Int?
        private var lastReported = -1
        func applyPendingStartIfReady(_ scrollView: UIScrollView) {
            guard let target = pendingStartIndex, target > 0, pageStride > 0 else {
                if pendingStartIndex == 0 { pendingStartIndex = nil }
                return
            }
            let wanted = CGFloat(target) * pageStride
            let maxOffset = scrollView.contentSize.height - scrollView.bounds.height
            guard scrollView.bounds.height > 0, maxOffset > 0 else { return }
            guard wanted <= maxOffset else { return }
            pendingStartIndex = nil
            lastReported = target
            scrollView.setContentOffset(CGPoint(x: 0, y: wanted), animated: false)
        }

        init(onPageChanged: @escaping (Int) -> Void, pageStride: CGFloat, doubleTapZoom: CGFloat) {
            self.onPageChanged = onPageChanged
            self.pageStride = pageStride
            self.doubleTapZoom = doubleTapZoom
        }

        // the zoomed subview is the whole page stack — that's what makes it a DOCUMENT zoom
        func viewForZooming(in scrollView: UIScrollView) -> UIView? { contentView }

        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            guard pageStride > 0, pageCount > 0 else { return }
            let raw = Int((scrollView.contentOffset.y / max(scrollView.zoomScale, 0.01)) / pageStride)
            let index = min(max(raw, 0), pageCount - 1)
            guard index != lastReported else { return }
            lastReported = index
            let callback = onPageChanged
            DispatchQueue.main.async { callback(index) }
        }

        @objc func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = gesture.view as? UIScrollView else { return }
            if scrollView.zoomScale > scrollView.minimumZoomScale {
                scrollView.setZoomScale(scrollView.minimumZoomScale, animated: true)
            } else {
                // zoom in around the tapped point, like Preview/Books
                let point = gesture.location(in: contentView)
                let size = scrollView.bounds.size
                let width = size.width / doubleTapZoom
                let height = size.height / doubleTapZoom
                scrollView.zoom(to: CGRect(x: point.x - width / 2, y: point.y - height / 2,
                                           width: width, height: height), animated: true)
            }
        }
    }
}

// 📖 23-Jul-2026: scroll view that reports each layout pass, so the "load fully, then jump to the
//   saved page" resume can be applied the moment the content is measured.
final class ResumeAwareScrollView: UIScrollView {
    var onLayout: ((UIScrollView) -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?(self)
    }
}

// MARK: - Native PDF reader (single-PDF scroll mode)
struct NativePdfScrollView: UIViewRepresentable {
    let path: String
    let startPageIndex: Int
    var ink: PdfInkAnchors? = nil
    let onPageChanged: (Int) -> Void

    func makeUIView(context: Context) -> PDFView {
        let pdfView = PDFView()
        pdfView.backgroundColor = .clear
        pdfView.displayMode = .singlePageContinuous   // traditional vertical scroll
        pdfView.displayDirection = .vertical
        pdfView.autoScales = true // fit-to-width, pinch to zoom from there
        pdfView.usePageViewController(false)
        // load the document ONCE, off the main thread, then attach on main
        let target = path
        DispatchQueue.global(qos: .userInitiated).async {
            let document = PDFDocument(url: URL(fileURLWithPath: target))
            DispatchQueue.main.async {
                guard let document else { return }
                pdfView.document = document
                // resume to the saved page once the document is attached
                let clamped = max(0, min(startPageIndex, document.pageCount - 1))
                if let page = document.page(at: clamped) { pdfView.go(to: page) }
            }
        }
        // observe page changes for the progress counter
        context.coordinator.observe(pdfView)
        ink?.attach(pdfView)
        return pdfView
    }

    func updateUIView(_ pdfView: PDFView, context: Context) {
        context.coordinator.onPageChanged = onPageChanged
    }

    func makeCoordinator() -> Coordinator { Coordinator(onPageChanged: onPageChanged) }

    final class Coordinator: NSObject {
        var onPageChanged: (Int) -> Void
        private weak var pdfView: PDFView?
        init(onPageChanged: @escaping (Int) -> Void) { self.onPageChanged = onPageChanged }

        func observe(_ pdfView: PDFView) {
            self.pdfView = pdfView
            NotificationCenter.default.addObserver(
                self, selector: #selector(pageChanged),
                name: .PDFViewPageChanged, object: pdfView)
        }

        @objc private func pageChanged() {
            guard let pdfView, let current = pdfView.currentPage,
                  let document = pdfView.document else { return }
            let index = document.index(for: current)
            onPageChanged(index)
        }

        deinit { NotificationCenter.default.removeObserver(self) }
    }
}

// MARK: - Native page-curl container

// 🔧 18-Jul-2026: UIPageViewController(.pageCurl) — the REAL paper-curl transition, wrapped for SwiftUI
struct BookPageCurlView: UIViewControllerRepresentable {
    let pages: [BookPageItem]
    let startIndex: Int
    var annotations: BookReaderAnnotations? = nil
    let onPageChanged: (Int) -> Void

    func makeUIViewController(context: Context) -> UIPageViewController {
        let controller = UIPageViewController(transitionStyle: .pageCurl, navigationOrientation: .horizontal)
        controller.dataSource = context.coordinator
        controller.delegate = context.coordinator
        controller.view.backgroundColor = .clear
        let start = min(max(startIndex, 0), pages.count - 1)
        controller.setViewControllers([context.coordinator.pageController(at: start)],
                                      direction: .forward, animated: false)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIPageViewController, context: Context) {
        context.coordinator.pages = pages
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(pages: pages, annotations: annotations, onPageChanged: onPageChanged)
    }

    // 🔧 18-Jul-2026: index travels on the hosting controller — no tag hacks
    final class BookPageHost: UIHostingController<AnyView> {
        let index: Int
        init(index: Int, page: BookPageItem, annotations: BookReaderAnnotations?) {
            self.index = index
            if let annotations {
                super.init(rootView: AnyView(AnnotatedBookPage(page: page, index: index, annotations: annotations)))
            } else {
                super.init(rootView: AnyView(BookPageContentView(page: page)))
            }
            view.backgroundColor = .clear
        }
        @MainActor required dynamic init?(coder aDecoder: NSCoder) { fatalError("not used") }
    }

    final class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate {
        var pages: [BookPageItem]
        let annotations: BookReaderAnnotations?
        let onPageChanged: (Int) -> Void
        init(pages: [BookPageItem], annotations: BookReaderAnnotations?, onPageChanged: @escaping (Int) -> Void) {
            self.pages = pages
            self.annotations = annotations
            self.onPageChanged = onPageChanged
        }

        func pageController(at index: Int) -> UIViewController {
            BookPageHost(index: index, page: pages[index], annotations: annotations)
        }

        func pageViewController(_ pvc: UIPageViewController, viewControllerBefore vc: UIViewController) -> UIViewController? {
            guard let host = vc as? BookPageHost, host.index > 0 else { return nil }
            return pageController(at: host.index - 1)
        }

        func pageViewController(_ pvc: UIPageViewController, viewControllerAfter vc: UIViewController) -> UIViewController? {
            guard let host = vc as? BookPageHost, host.index < pages.count - 1 else { return nil }
            return pageController(at: host.index + 1)
        }

        func pageViewController(_ pvc: UIPageViewController, didFinishAnimating finished: Bool,
                                previousViewControllers: [UIViewController], transitionCompleted completed: Bool) {
            guard completed, let host = pvc.viewControllers?.first as? BookPageHost else { return }
            onPageChanged(host.index)
        }
    }
}

// MARK: - Page faces

struct MaybeZoomable<Content: View>: View {
    let enabled: Bool
    @ViewBuilder var content: Content

    var body: some View {
        if enabled {
            ZoomableView { content }
        } else {
            content
        }
    }
}


struct BookPageContentView: View {
    let page: BookPageItem

    var allowPageZoom: Bool = true
    var ink: AnyView? = nil
    @State private var showPreview = false

    var body: some View {
        ZStack {
            // paper sheet with a soft spine shadow on the left
            (isCover ? BookPalette.cover : BookPalette.paper)
            HStack(spacing: 0) {
                LinearGradient(colors: [Color.black.opacity(0.18), .clear],
                               startPoint: .leading, endPoint: .trailing)
                    .frame(width: 14)
                Spacer()
            }
            pageBody.padding(.horizontal, 4).padding(.vertical, 4)
            if !isPdf, let ink {
                ink
            }
        }
    }

    private var isPdf: Bool { if case .pdf = page { return true }; return false }

    private var isCover: Bool { if case .cover = page { return true }; return false }

    @ViewBuilder
    private var pageBody: some View {
        switch page {
        case .cover(let title, let subtitle):
            VStack(spacing: 14) {
                Text(title).font(.system(.title, design: .serif).weight(.semibold))
                    .foregroundColor(BookPalette.paper).multilineTextAlignment(.center)
                Rectangle().fill(BookPalette.paper.opacity(0.6)).frame(width: 60, height: 2)
                Text(subtitle).font(.subheadline).foregroundColor(BookPalette.paper.opacity(0.8))
            }

        case .text(let body, let chunk, let chunkCount, _):
            VStack {
                ScrollView {
                    Text(body)
                        .font(.system(.body, design: .serif))
                        .lineSpacing(6)
                        .foregroundColor(BookPalette.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if chunkCount > 1 {
                    Text("· \(chunk) of \(chunkCount) ·").font(.caption2)
                        .foregroundColor(BookPalette.ink.opacity(0.5))
                }
            }

        case .image(let path, let title, _):
            VStack(spacing: 8) {

                MaybeZoomable(enabled: allowPageZoom) {
                    if FileManager.default.fileExists(atPath: path), let ui = UIImage(contentsOfFile: path) {
                        Image(uiImage: ui).resizable().scaledToFit()
                    } else {
                        AsyncImage(url: URL(string: path)) { img in img.resizable().scaledToFit() }
                            placeholder: { ProgressView() }
                    }
                }
                if !title.isEmpty {
                    Text(title).font(.caption.italic()).foregroundColor(BookPalette.ink.opacity(0.7)).lineLimit(1)
                }
            }

        case .pdf(let path, let pageIndex, _, _, _):
            VStack(spacing: 6) {
            MaybeZoomable(enabled: allowPageZoom) {
                    PdfSheetView(path: path, pageIndex: pageIndex, ink: ink)
                }
            }

        case .media(let media):
            MediaBookPageView(media: media)

        case .docFile(let media):
            VStack(spacing: 12) {
                ZStack {
                    Circle().fill(BookPalette.cover.opacity(0.12)).frame(width: 74, height: 74)
                    Image(systemName: iconNameForContentType(media.type))
                        .font(.system(size: 30)).foregroundColor(BookPalette.cover)
                }
                Text(media.title.isEmpty ? "File" : media.title)
                    .font(.subheadline.weight(.semibold)).foregroundColor(BookPalette.ink)
                    .multilineTextAlignment(.center)
                Text([media.type.name, readableFileSize(media.sizeBytes)].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption).foregroundColor(BookPalette.ink.opacity(0.6))
                Button { showPreview = true } label: {
                    Label("Open", systemImage: "arrow.up.forward.square")
                }.buttonStyle(.borderedProminent).tint(BookPalette.cover)
            }
            .sheet(isPresented: $showPreview) {
                if let path = LocalFilePathResolver_iosKt.resolveLocalFilePath(path: media.localPath) ?? media.localPath {
                    QuickLookPreview(fileURL: URL(fileURLWithPath: path))
                }
            }

        case .link(let url, _):
            VStack(spacing: 12) {
                Text("A link lives on this page").font(.headline).foregroundColor(BookPalette.ink)
                Text(url).font(.footnote).foregroundColor(.blue).multilineTextAlignment(.center)
                Button("Open link") {
                    if let u = URL(string: url) { UIApplication.shared.open(u) }
                }.buttonStyle(.borderedProminent).tint(BookPalette.cover)
            }

        case .location(let lat, let lon, let address, _):
            VStack(spacing: 10) {
                Image(systemName: "mappin.and.ellipse").font(.system(size: 36)).foregroundColor(BookPalette.cover)
                Text(address ?? String(format: "%.5f, %.5f", lat, lon))
                    .font(.subheadline).foregroundColor(BookPalette.ink).multilineTextAlignment(.center)
                Button("Open in Maps") {
                    if let u = URL(string: "http://maps.apple.com/?ll=\(lat),\(lon)&q=\(lat),\(lon)") {
                        UIApplication.shared.open(u)
                    }
                }.buttonStyle(.borderedProminent).tint(BookPalette.cover)
            }
        }
    }
}
enum PdfSheetCache {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 24   // keep a small working set of recently-seen pages
        return c
    }()
    static func key(path: String, pageIndex: Int) -> NSString { "\(path)#\(pageIndex)" as NSString }
    static func image(path: String, pageIndex: Int) -> UIImage? { cache.object(forKey: key(path: path, pageIndex: pageIndex)) }
    static func store(_ image: UIImage, path: String, pageIndex: Int) { cache.setObject(image, forKey: key(path: path, pageIndex: pageIndex)) }
}

actor PdfRenderGate {
    static let shared = PdfRenderGate(limit: 2)   // at most 2 pages rendering concurrently
    private let limit: Int
    private var active = 0
    private var waiters: [CheckedContinuation<Void, Never>] = []
    init(limit: Int) { self.limit = limit }

    /// Returns true once a render slot is held. Returns false if the task was cancelled while waiting
    /// (a page scrolled past), so the caller does NOT need to release — the slot was never granted.
    func acquire() async -> Bool {
        if Task.isCancelled { return false }
        if active < limit { active += 1; return true }
        await withCheckedContinuation { waiters.append($0) }
        // resumed either by a release (slot is ours) — honour cancellation but the slot IS granted here
        active += 1
        return true
    }
    func release() {
        active -= 1
        if !waiters.isEmpty { waiters.removeFirst().resume() }
    }
}

// 🔧 18-Jul-2026: one PDF page rendered as an image via PDFKit thumbnails (crisp + cheap)
struct PdfSheetView: View {
    let path: String
    let pageIndex: Int
    var ink: AnyView? = nil
    @State private var image: UIImage?
    // 🐛 23-Jul-2026 FIX (infinite loader): a null image was indistinguishable from "still loading",
    //   so if the render ever produced nothing the sheet span forever. Track a done flag and show a
    //   clear state instead of a permanent spinner.
    @State private var didFinish = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFit()
                    .overlay { ink }
            } else if didFinish {
                // rendered but empty — better than an endless spinner
                Color.clear
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task(id: "\(path)#\(pageIndex)") {
    
            if image != nil { return }
            // 📖 25-Jul-2026: reuse the already-rendered page across mode switches / re-scrolls
            if let cached = PdfSheetCache.image(path: path, pageIndex: pageIndex) {
                image = cached; didFinish = true; return
            }
           
            let acquired = await PdfRenderGate.shared.acquire()
            guard acquired, !Task.isCancelled else {
                if acquired { await PdfRenderGate.shared.release() }
                didFinish = true
                return
            }
            let rendered = await Self.render(path: path, pageIndex: pageIndex)
            await PdfRenderGate.shared.release()
            if Task.isCancelled { return }
            await MainActor.run {
                if let rendered { PdfSheetCache.store(rendered, path: path, pageIndex: pageIndex) }
                image = rendered
                didFinish = true
            }
        }
    }
    private static let fallbackPageSize = CGSize(width: 612, height: 792) // US-Letter @72dpi

    private static let maxRenderDimension: CGFloat = 1100

    private static func render(path: String, pageIndex: Int) async -> UIImage? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                guard let doc = PDFDocument(url: URL(fileURLWithPath: path)),
                      pageIndex >= 0, pageIndex < doc.pageCount,
                      let pdfPage = doc.page(at: pageIndex) else {
                    continuation.resume(returning: nil)
                    return
                }
                // pick a usable box: mediaBox, else cropBox, else a sane default — never 0/NaN
                var bounds = pdfPage.bounds(for: .mediaBox)
                if !bounds.width.isFinite || !bounds.height.isFinite ||
                    bounds.width <= 1 || bounds.height <= 1 {
                    bounds = pdfPage.bounds(for: .cropBox)
                }
                let width = (bounds.width.isFinite && bounds.width > 1) ? bounds.width : Self.fallbackPageSize.width
                let height = (bounds.height.isFinite && bounds.height > 1) ? bounds.height : Self.fallbackPageSize.height
                // longest-side clamp (handles portrait AND landscape pages) at ≤1.5× native
                let longest = max(width, height)
                let scale = min(1.5, Self.maxRenderDimension / max(longest, 1))
                let targetSize = CGSize(width: max(width * scale, 1), height: max(height * scale, 1))
                // final guard: a non-finite/degenerate size must never reach CoreGraphics
                guard targetSize.width >= 1, targetSize.height >= 1,
                      targetSize.width.isFinite, targetSize.height.isFinite else {
                    continuation.resume(returning: nil)
                    return
                }
                let rendered = pdfPage.thumbnail(of: targetSize, for: bounds.width > 1 ? .mediaBox : .cropBox)
                continuation.resume(returning: rendered)
            }
        }
    }
}

struct MediaBookPageView: View {
    let media: NoteContentModel.MediaContent
    private let mediaManager = MediaManager.mediaManager

    var body: some View {
        VStack(spacing: 10) {
            Text(media.title.isEmpty ? media.type.name : media.title)
                .font(.subheadline.weight(.semibold)).foregroundColor(BookPalette.ink).lineLimit(1)

            // 🔧 30-Jul-2026 02:10 editor's players, reader's paper background around them.
            if media.type == ContentType.audio {
               AudioPlayView(mediaContent: media)
            } else {
               VideoCardPlayer(content: media)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
     
        .onAppear {
            if mediaManager.currentPlaying?.id != media.id {
                mediaManager.prepareMedia(media: media)
            }
        }
    }
}


enum BookWidgetStore {
    static let appGroupId = "group.com.app.pustakam"
    static let lastIdKey = "book.last.noteId"
    static let lastTitleKey = "book.last.title"

    static func saveLastBook(noteId: String, title: String) {
        let defaults = UserDefaults(suiteName: appGroupId) ?? .standard
        defaults.set(noteId, forKey: lastIdKey)
        defaults.set(title.isEmpty ? "Untitled note" : title, forKey: lastTitleKey)
        WidgetCenter.shared.reloadAllTimelines()   // refresh the book widget cover
    }
}
private enum ReaderPreviewData {
    static let cover = BookPageItem.cover(title: "Field Notes", subtitle: "6 entries")

    /// The one to look at: a single short line should NOT need a whole sheet.
    static let shortText = BookPageItem.text(
        body: "One short line — this is the case that exposes the full-page problem.",
        chunk: 1, chunkCount: 1, sourceId: "t1"
    )

    static let longText = BookPageItem.text(
        body: String(
            repeating: "Paragraph text that runs long enough to fill a sheet and show how the paper "
                + "frame, margins and typography behave when a page is genuinely full. ",
            count: 6
        ),
        chunk: 1, chunkCount: 3, sourceId: "t2"
    )

    static let link = BookPageItem.link(url: "https://example.com/a-reference", sourceId: "l1")

    static let location = BookPageItem.location(
        lat: 19.076, lon: 72.8777, address: "Mumbai, Maharashtra", sourceId: "loc1"
    )
}

#Preview("Page · cover") {
    BookPageContentView(page: ReaderPreviewData.cover)
        .background(BookPalette.desk)
}

#Preview("Page · short text") {
    BookPageContentView(page: ReaderPreviewData.shortText)
        .background(BookPalette.desk)
}

#Preview("Page · long text") {
    BookPageContentView(page: ReaderPreviewData.longText)
        .background(BookPalette.desk)
}

#Preview("Page · link") {
    BookPageContentView(page: ReaderPreviewData.link)
        .background(BookPalette.desk)
}

#Preview("Page · location") {
    BookPageContentView(page: ReaderPreviewData.location)
        .background(BookPalette.desk)
}

#Preview("Scroll · mixed content") {
    BookScrollReader(
        pages: [
            ReaderPreviewData.cover,
            ReaderPreviewData.shortText,
            ReaderPreviewData.link,
            ReaderPreviewData.location,
            ReaderPreviewData.longText,
        ],
        startIndex: 0,
        onPageChanged: { _ in }
    )
    .background(BookPalette.desk)
}
