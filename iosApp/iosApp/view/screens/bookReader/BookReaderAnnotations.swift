import Combine
import PDFKit
import SwiftUI
import shared

final class BookReaderAnnotations: ObservableObject {

    private static let saveDelay: TimeInterval = 0.4

    @Published private(set) var inkCaptures = false

    @Published private(set) var writable = true

    var onContents: (([NoteContentModel]) -> Void)?

    private let adapter: NotesBridgeAdapter

    private let contentBridge: NoteContentBridge

    private var note: Note?

    private var documentId: String?

    private var contentsHandle: Closeable?

    private var pendingId: String?

    private var saveWork: DispatchWorkItem?

    private var changes = Set<AnyCancellable>()

    private(set) lazy var drawing = DrawingHost(
        contents: { [weak self] in self?.note?.contents ?? [] },
        noteId: { [weak self] in self?.note?.id },
        documentId: { [weak self] in self?.documentId },
        onWrite: { [weak self] in self?.saveAnnotation($0) }
    )

    private(set) lazy var pdfInk = PdfInkAnchors { [weak self] index in self?.anchorId(index) }

    init(adapter: NotesBridgeAdapter = NotesBridgeAdapter(), contentBridge: NoteContentBridge = NoteContentBridge()) {
        self.adapter = adapter
        self.contentBridge = contentBridge
        drawing.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &changes)
        drawing.$overlay
            .map { $0?.capturesTouches ?? Just(false).eraseToAnyPublisher() }
            .switchToLatest()
            .removeDuplicates()
            .sink { [weak self] in self?.inkCaptures = $0 }
            .store(in: &changes)
    }

    deinit {
        flushAnnotation()
        contentsHandle?.close()
        contentBridge.dispose()
    }

    var annotating: Bool { drawing.isOverlayActive() }

    var contents: [NoteContentModel] { note?.contents ?? [] }

    var zoomLocked: Bool { annotating && inkCaptures }

    func anchorId(_ pageIndex: Int) -> String? {
        documentId.map { DrawNoteContents.shared.pageAnchorId(targetId: $0, pageIndex: Int32(pageIndex)) }
    }

    func followNote(noteId: String, documentId: String) {
        self.documentId = documentId
        guard !noteId.isEmpty, contentsHandle == nil else { return }
        adapter.readNote(noteId: noteId) { [weak self] result in
            guard let self, case .success(let note) = result, let note else { return }
            self.writable = note.share?.canWrite() != false
            self.note = note
            self.refresh(note.contents)
        }
        contentsHandle = contentBridge.observeContents(noteId: noteId) { [weak self] contents in
            guard let self else { return }
            self.note = self.note?.withContents(newContents: contents)
            self.refresh(contents)
        }
    }

    func target(documentId: String) {
        guard self.documentId != documentId else { return }
        self.documentId = documentId
        drawing.sync(contents)
    }

    func write(_ content: NoteContentModel) {
        guard let note, writable else { return }
        let next = note.withContents(newContents: note.contents.filter { $0.id != content.id } + [content])
        self.note = next
        refresh(next.contents)
        adapter.createOrUpdateNote(note: next, dirtyContentIds: [content.id]) { _ in }
    }

    func remove(contentId: String) {
        guard let note, writable else { return }
        let next = note.withContents(newContents: note.contents.filter { $0.id != contentId })
        self.note = next
        refresh(next.contents)
        adapter.deleteNoteContent(contentId: contentId) { _ in }
    }

    private func refresh(_ contents: [NoteContentModel]) {
        drawing.sync(contents)
        onContents?(contents)
    }

    func flushAnnotation() {
        saveWork?.cancel()
        saveWork = nil
        guard let note, let id = pendingId else { return }
        pendingId = nil
        adapter.createOrUpdateNote(note: note, dirtyContentIds: [id]) { _ in }
    }

    private func saveAnnotation(_ content: NoteContentModel.Drawing) {
        guard let note, writable else { return }
        let others = note.contents.filter { $0.id != content.id }
        self.note = note.withContents(newContents: others + [content as NoteContentModel])
        pendingId = content.id
        saveWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.flushAnnotation() }
        saveWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.saveDelay, execute: work)
    }
}

final class PdfInkAnchors {

    private let anchorId: (Int) -> String?

    private weak var pdfView: PDFView?

    private var tokens: [NSObjectProtocol] = []

    private var observation: NSKeyValueObservation?

    private(set) lazy var anchors = DrawingAnchors { [weak self] _ in self?.frames() ?? [] }

    init(anchorId: @escaping (Int) -> String?) {
        self.anchorId = anchorId
    }

    deinit {
        tokens.forEach { NotificationCenter.default.removeObserver($0) }
    }

    func attach(_ view: PDFView) {
        guard view !== pdfView else { return }
        tokens.forEach { NotificationCenter.default.removeObserver($0) }
        pdfView = view
        observation = nil
        let names: [Notification.Name] = [.PDFViewDocumentChanged, .PDFViewScaleChanged, .PDFViewPageChanged, .PDFViewVisiblePagesChanged]
        tokens = names.map { name in
            NotificationCenter.default.addObserver(forName: name, object: view, queue: .main) { [weak self] _ in
                self?.refresh()
            }
        }
        refresh()
    }

    func refresh() {
        if observation == nil, let scroll = pdfView.flatMap(Self.scrollView(in:)) {
            observation = scroll.observe(\.contentOffset, options: [.new]) { [weak self] _, _ in
                self?.anchors.refresh()
            }
        }
        anchors.refresh()
    }

    private func frames() -> [DrawAnchorFrame] {
        guard let pdfView, let document = pdfView.document else { return [] }
        return pdfView.visiblePages.compactMap { page in
            guard let id = anchorId(document.index(for: page)) else { return nil }
            let rect = pdfView.convert(page.bounds(for: pdfView.displayBox), from: page)
            return DrawCommands.shared.anchor(
                id: id,
                x: Float(rect.minX),
                y: Float(rect.minY),
                width: Float(rect.width),
                height: Float(rect.height)
            )
        }
    }

    private static func scrollView(in view: UIView) -> UIScrollView? {
        for subview in view.subviews {
            if let scroll = subview as? UIScrollView { return scroll }
            if let nested = scrollView(in: subview) { return nested }
        }
        return nil
    }
}

struct AnnotatedBookPage: View {

    let page: BookPageItem
    let index: Int
    var allowPageZoom: Bool = true
    @ObservedObject var annotations: BookReaderAnnotations

    var body: some View {
        BookPageContentView(page: page, allowPageZoom: allowPageZoom && !annotations.zoomLocked, ink: ink)
    }

    private var ink: AnyView? {
        guard let session = annotations.drawing.overlay, let anchorId = annotations.anchorId(index) else { return nil }
        return AnyView(DrawingPageLayer(session: session, anchorId: anchorId, active: annotations.annotating))
    }
}
