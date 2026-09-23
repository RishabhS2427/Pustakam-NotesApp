import shared
import Combine
import SwiftUI

final class MasterEditorViewModel: ObservableObject {

    @Published private(set) var note: Note?
    @Published private(set) var noteContents: [NoteContentModel] = []
    @Published private(set) var canvas = CanvasCommands.shared.initialState()
    @Published private(set) var texts: [String: MasterTextState] = [:]
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var capabilities = EditorCapabilityCommands.shared.empty()
    @Published var keyboardDismissToken: Int = 0

    private let adapter: NotesBridgeAdapter
    private let canvasBridge: CanvasBridgeAdapter
    private let contentBridge = NoteContentBridge()
    private var dirtyContentIds = Set<String>()
    private var hydratedNoteId: String?
    private var pendingStored: (noteId: String, nodes: [CanvasNode])?
    private var contentSyncHandle: Closeable?
    private var remoteCanvasHandle: Closeable?
    private var canvasReady = false
    // 🔄 24-Sep-2026 — another device's canvas waits here while a finger is busy; what this device touches meanwhile stays its own
    private var pendingRemote: [CanvasNode]?
    private var touchedWhilePending = Set<String>()
    // 🔄 24-Sep-2026 — the board as it was when a drag or resize began, so letting go without moving stamps nothing
    private var gestureStart: CanvasDocument?

    private var commands: CanvasCommands { CanvasCommands.shared }

    private var textContents: [NoteContentModel.TextContent] {
        noteContents.compactMap { $0 as? NoteContentModel.TextContent }
    }

    init(
        noteId: String? = nil,
        adapter: NotesBridgeAdapter = NotesBridgeAdapter(),
        canvasBridge : CanvasBridgeAdapter = CanvasBridgeAdapter()
    ) {
        self.adapter = adapter
        self.canvasBridge = canvasBridge
        load(noteId: noteId)
    }

    deinit {
        contentSyncHandle?.close()
        remoteCanvasHandle?.close()
        contentBridge.dispose()
    }

    func text(for nodeId: String) -> MasterTextState? { texts[nodeId] }

    func content(for node: CanvasNode) -> NoteContentModel? {
        guard let contentId = node.contentId else { return nil }
        return noteContents.first { $0.id == contentId }
    }

    // MARK: - Load

    /// Re-reads the note that is already open. Never passes nil, so coming back to the screen
    /// cannot create a second note — mirrors NoteEditorViewModel.refresh().
    func refresh() {
        guard dirtyContentIds.isEmpty else { return }   // don't overwrite unsaved edits
        guard let id = note?.id, !id.isEmpty else { return }
        load(noteId: id)
    }

    /// A nil id is passed straight through, the same as the note editor does: the repository
    /// answers with a fresh empty note rather than nothing at all.
    func load(noteId: String?) {
        adapter.readNote(noteId: noteId) { [weak self] result in
            guard let self else { return }
            switch result {
            case .loading:
                self.isLoading = true
            case .success(let note):
                self.isLoading = false
                self.errorMessage = nil
                if let note { self.apply(note: note) }
            case .failure(let error):
                self.isLoading = false
                // a stale or deleted id must not dead-end the screen. Retry with nil, which is
                // the repository's "create an empty note" path, exactly as opening with no id.
                if noteId != nil {
                    self.load(noteId: nil)
                } else {
                    self.errorMessage = error.message
                }
            case .idle:
                break
            }
        }
    }

    private func apply(note: Note) {
        self.note = note
        if dirtyContentIds.isEmpty {
            noteContents = note.contents
        }
        // 🎧 the canvas plays through the SAME shared player as the note editor — it needs this note's media list
        contentBridge.setSelectedNote(note: note)
        guard hydratedNoteId != note.id else {
            refreshMissingTexts()
            return
        }
        hydratedNoteId = note.id
        observeExternalContents(noteId: note.id)
        observeRemoteCanvas(noteId: note.id)
        hydrateCanvas(noteId: note.id)
    }

    private func observeExternalContents(noteId: String) {
        contentSyncHandle?.close()
        contentSyncHandle = contentBridge.observeContents(noteId: noteId) { [weak self] contents in
            guard let self else { return }
            self.onEditorIntent(
                EditorCommands.shared.externalContentsChanged(contents: contents)
            )
            self.adoptOrphanContents()
            self.refreshMissingTexts()
        }
    }

    // 🔄 24-Sep-2026 — a widget whose content was deleted elsewhere is dropped before the board is read
    private func hydrateCanvas(noteId: String) {
        canvasBridge.pruneOrphans { [weak self] in
            self?.readStoredCanvas(noteId: noteId)
        }
    }

    // 🔄 24-Sep-2026 — another device's canvas for this note, applied once no finger is busy
    private func observeRemoteCanvas(noteId: String) {
        remoteCanvasHandle?.close()
        remoteCanvasHandle = canvasBridge.observeRemoteCanvas(noteId: noteId) { [weak self] nodes in
            guard let self else { return }
            self.pendingRemote = nodes
            self.touchedWhilePending.removeAll()
            self.adoptPendingRemote()
        }
    }

    private func adoptPendingRemote() {
        guard let nodes = pendingRemote, canvasReady, commands.canAdoptRemote(state: canvas) else { return }
        let keep = touchedWhilePending
        pendingRemote = nil
        touchedWhilePending.removeAll()
        dispatchCanvas(commands.adoptRemote(nodes: nodes, keepLocal: keep), housekeeping: true)
        refreshMissingTexts()
    }

    /// 📄 23-Sep-2026 — a page now CARRIES contents instead of standing for one, so a stored
    /// canvas takes one of three paths: an old one is upgraded in place (pages keep their id,
    /// name and rect), an empty one is laid out by CanvasPaginator through the reading-mode
    /// grouping, and a current one only has to be guaranteed at least one page. Mirrors
    /// MasterEditorViewModel.applyCanvas on Android.
    private func readStoredCanvas(noteId: String) {
        canvasBridge.readCanvas(noteId: noteId) { [weak self] result in
            guard let self else { return }
            switch result {
            case .loading:
                self.isLoading = true
            case .success(let document):
                self.isLoading = false
                let stored: [CanvasNode] = document?.nodes ?? []
                // pages are cut to the fitted screen, so a canvas waits for the board's size
                if self.canvas.viewport.widthPx > 0 {
                    self.lay(noteId: noteId, stored: stored)
                } else {
                    self.pendingStored = (noteId: noteId, nodes: stored)
                }
            case .failure(let error):
                self.isLoading = false
                self.errorMessage = error.message
                self.hydratedNoteId = nil
                self.render(nodes: self.laidOut(noteId: noteId, stored: []), viewport: nil)
            case .idle:
                break
            }
        }
    }

    private func lay(noteId: String, stored: [CanvasNode]) {
        let rewritten = stored.isEmpty
            || CanvasPaginator.shared.needsUpgrade(nodes: stored)
            || CanvasPaginator.shared.pagesMissing(nodes: stored)
        let nodes = laidOut(noteId: noteId, stored: stored)
        if rewritten {
            canvasBridge.saveAll(noteId: noteId, nodes: nodes)
        }
        // 🔄 24-Sep-2026 — no saveNote() here any more: the seeded text block already writes a brand-new note, and re-saving an existing one on open would stamp it
        readViewport(noteId: noteId, nodes: nodes)
    }

    private func laidOut(noteId: String, stored: [CanvasNode]) -> [CanvasNode] {
        let page = stored.first { $0.isPage }
        let width = page?.rect.width ?? commands.fittedPageWidth(state: canvas)
        let height = page?.rect.height ?? commands.fittedPageHeight(state: canvas)
        let paginator = CanvasPaginator.shared
        if stored.isEmpty {
            seedFirstTextContent(noteId: noteId)
            return paginator.build(contents: noteContents, pageWidth: width, pageHeight: height)
                .nodes
        }
        if paginator.needsUpgrade(nodes: stored) {
            return paginator.upgrade(nodes: stored, pageWidth: width, pageHeight: height).nodes
        }
        return paginator.ensurePageIn(nodes: stored, pageWidth: width, pageHeight: height).nodes
    }

    /// An empty note still needs one text row, otherwise its first page has nothing to type into.
    private func seedFirstTextContent(noteId: String) {
        guard noteContents.isEmpty else { return }
        addContent(
            NoteContentObjectHelper.shared.createText(noteId: noteId, positionedAt: 0, text: "")
        )
    }

    private func readViewport(noteId: String, nodes: [CanvasNode]) {
        canvasBridge.readViewport(noteId: noteId) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let viewport):
                self.render(nodes: nodes, viewport: viewport)
            case .failure:
                self.render(nodes: nodes, viewport: nil)
            case .loading, .idle:
                break
            }
        }
    }

    private func render(nodes: [CanvasNode], viewport: Viewport?) {
        texts = buildTexts(nodes: nodes, contents: textContents, keeping: texts)
        canvas = commands.loaded(state: canvas, nodes: nodes, viewport: viewport)
        canvasReady = true
        // 🔄 24-Sep-2026 — a canvas that arrived meanwhile lands first; contents still without a widget get one after it
        adoptPendingRemote()
        adoptOrphanContents()
    }

    // 🔄 24-Sep-2026 — housekeeping, never a user edit: a content that arrived without a widget gets one
    private func adoptOrphanContents() {
        guard commands.pageForSpawn(state: canvas) != nil else { return }
        for content in noteContents where canvas.document.nodeForContent(contentId: content.id) == nil {
            spawnWidgetNode(kind: content.type, contentId: content.id, housekeeping: true)
        }
    }

    private func refreshMissingTexts() {
        var updated = texts
        var changed = false
        let byContentId = Dictionary(
            textContents.map { ($0.id, $0) },
            uniquingKeysWith: { _, last in last }
        )
        for node in canvas.document.nodes where node.isTextWidget {
            guard let contentId = node.contentId,
                  let content = byContentId[contentId],
                  !dirtyContentIds.contains(contentId) else { continue }
            let document = RichTextCodec.shared.documentFrom(content: content)
            if updated[node.id]?.document != document {
                updated[node.id] = MasterTextState.companion.of(document: document)
                changed = true
            }
        }

        let liveIds = Set(canvas.document.nodes.map { $0.id })
        for id in updated.keys where !liveIds.contains(id) {
            updated.removeValue(forKey: id)
            changed = true
        }
        if changed { texts = updated }
    }

    private func buildTexts(
        nodes: [CanvasNode],
        contents: [NoteContentModel.TextContent],
        keeping existing: [String: MasterTextState] = [:]
    ) -> [String: MasterTextState] {
        var built: [String: MasterTextState] = [:]
        for node in nodes where node.isTextWidget {
            guard let contentId = node.contentId else { continue }
            if let live = existing[node.id] {
                built[node.id] = live
                continue
            }
            guard let content = contents.first(where: { $0.id == contentId }) else { continue }
            built[node.id] = MasterTextState.companion.of(
                document: RichTextCodec.shared.documentFrom(content: content)
            )
        }
        return built
    }

    // MARK: - Content editing

    private func editorState() -> EditorState {
        EditorCommands.shared.stateOf(
            note: note,
            contents: noteContents,
            dirtyContentIds: dirtyContentIds,
            isLoading: isLoading,
            error: errorMessage,
            capabilities: capabilities
        )
    }

    func onEditorIntent(_ intent: any EditorIntent) {
        let commands = EditorCommands.shared
        let before = editorState()
        let next = commands.reduce(state: before, intent: intent)

        note = next.noteWithContents() ?? next.note
        noteContents = next.contents
        dirtyContentIds = next.dirtyContentIds
        isLoading = next.isLoading
        errorMessage = next.error
        capabilities = next.capabilities

        for effect in commands.effects(before: before, next: next, intent: intent) {
            runEffect(effect)
        }
    }

    private func runEffect(_ effect: any EditorEffect) {
        let commands = EditorCommands.shared

        if let read = commands.readNoteEffect(effect: effect) {
            load(noteId: read.noteId)

        } else if let save = commands.saveNoteEffect(effect: effect) {
            let dirty = save.dirtyContentIds
            adapter.createOrUpdateNote(note: save.note, dirtyContentIds: dirty) { [weak self] result in
                switch result {
                case .success(let saved):
                    self?.dirtyContentIds.subtract(dirty)
                    if let saved { self?.note = saved }
                case .failure(let error):
                    self?.errorMessage = error.message
                case .loading, .idle:
                    break
                }
            }

        } else if let row = commands.deleteContentRowEffect(effect: effect) {
            adapter.deleteNoteContent(contentId: row.contentId) { _ in }

        } else if let files = commands.deleteFilesEffect(effect: effect) {
            for path in files.paths {
                deleteFile(
                    filePath: LocalFilePathResolver_iosKt.resolveLocalFilePath(path: path) ?? path
                )
            }

        } else if let publish = commands.publishMediaEffect(effect: effect) {
            contentBridge.updateMediaContent(content: publish.content)

        } else if let thumbnail = commands.makeThumbnailEffect(effect: effect) {
            makeThumbnail(for: thumbnail.content)
        }
    }

    private func makeThumbnail(for media: NoteContentModel.MediaContent) {
        guard let path = media.localPath, !path.isEmpty else { return }
        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let thumb = generateThumbnail(sourcePath: path, type: media.type) else { return }
            DispatchQueue.main.async {
                self?.onEditorIntent(
                    EditorCommands.shared.thumbnailReady(contentId: media.id, thumbnailPath: thumb)
                )
            }
        }
    }

    private func addContent(_ content: NoteContentModel) {
        onEditorIntent(EditorCommands.shared.addContent(content: content))
    }

    private func updateContent(_ content: NoteContentModel) {
        onEditorIntent(EditorCommands.shared.updateContent(content: content))
    }

    private func removeContent(id: String) {
        onEditorIntent(EditorCommands.shared.removeContent(contentId: id))
    }

    func saveNote() {
        guard let note else { return }
        let toSave = note.withContents(newContents: noteContents)
        let dirtySnapshot = dirtyContentIds
        adapter.createOrUpdateNote(note: toSave, dirtyContentIds: dirtySnapshot) { [weak self] result in
            switch result {
            case .success:
                self?.dirtyContentIds.subtract(dirtySnapshot)
            case .failure(let error):
                self?.errorMessage = error.message
                print("MasterEditor saveNote failed [\(error.code)] \(error.message)")
            case .loading, .idle:
                break
            }
        }
    }

    // MARK: - Canvas

    func onCanvasIntent(_ intent: CanvasEditorIntent) {
        dispatchCanvas(intent, housekeeping: false)
    }

    // 🔄 24-Sep-2026 — housekeeping (a fit, a measure, a placed orphan, another device's canvas) is written but never stamps the note
    private func dispatchCanvas(_ intent: CanvasEditorIntent, housekeeping: Bool) {
        let before = canvas
        canvas = CanvasEditorReducer.shared.reduce(state: before, intent: intent)
        // 📄 leaving edit mode — a tap on paper or bare canvas — puts the keyboard away
        if before.editingNodeId != nil && canvas.editingNodeId == nil {
            keyboardDismissToken &+= 1
        }
        if commands.gestureStarted(before: before, next: canvas) {
            gestureStart = canvas.document
        }
        let edited = !housekeeping && commands.editsLayout(
            before: before,
            next: canvas,
            intent: intent,
            gestureStart: gestureStart
        )
        if commands.gestureEnded(before: before, next: canvas) {
            gestureStart = nil
        }
        persistCanvas(before: before, intent: intent, edited: edited)
        if let pending = pendingStored, canvas.viewport.widthPx > 0 {
            pendingStored = nil
            lay(noteId: pending.noteId, stored: pending.nodes)
        }
        adoptPendingRemote()
    }

    /// 📄 23-Sep-2026 — one rule for every intent: whatever the reduce changed is written, what
    /// it removed is deleted, and the note's content order follows the canvas when that changed.
    /// CanvasCommands decides what "changed" means, so Android writes exactly the same rows.
    private func persistCanvas(before: CanvasEditorState, intent: CanvasEditorIntent, edited: Bool) {
        guard let noteId = note?.id else { return }
        let changed: [CanvasNode] = commands.nodesToSave(before: before, next: canvas)
        let removed: [String] = commands.removedNodeIds(before: before, next: canvas)
        if pendingRemote != nil {
            touchedWhilePending.formUnion(changed.map { $0.id })
            touchedWhilePending.formUnion(removed)
        }
        if edited {
            // 🔄 the rows land first; only then is the note stamped, so the push that follows carries them
            canvasBridge.saveEdit(noteId: noteId, nodes: changed, removedIds: removed) { [weak self] in
                self?.onEditorIntent(EditorCommands.shared.saveRequested())
            }
        } else {
            if !changed.isEmpty {
                canvasBridge.saveAll(noteId: noteId, nodes: changed)
            }
            for removedId in removed {
                canvasBridge.remove(nodeId: removedId)
            }
        }
        // 🔄 only a user's edit re-orders the note; housekeeping and another device's canvas never re-stamp it
        if edited && commands.orderChanged(before: before, next: canvas) {
            applyCanvasOrderToNote()
        }
        if canvas.viewport != before.viewport && commands.affectsViewport(intent: intent) {
            canvasBridge.saveViewport(noteId: noteId, viewport: canvas.viewport)
        }
    }

    // MARK: - Text

    func onTextIntent(nodeId: String, intent: MasterTextIntent) {
        guard let current = texts[nodeId] else { return }
        let next = MasterTextReducer.shared.reduce(state: current, intent: intent)
        texts[nodeId] = next
        if next.document != current.document { persistText(nodeId: nodeId, state: next) }
    }

    private func persistText(nodeId: String, state: MasterTextState) {
        guard let contentId = canvas.document.nodeById(nodeId: nodeId)?.contentId,
              let content = textContents.first(where: { $0.id == contentId }) else { return }
        updateContent(RichTextCodec.shared.applyTo(content: content, document: state.document))
    }

    // MARK: - Nodes

    // 📄 24-Sep-2026 — a new page is totally blank: fresh paper beside the last page, fitted to the screen, nothing on it
    func addPage() {
        guard note != nil else { return }
        let page = addBarePage(housekeeping: false)
        onCanvasIntent(commands.selectNode(nodeId: page.id))
    }

    private func addBarePage(housekeeping: Bool) -> CanvasNode {
        let page = commands.pageNode(state: canvas)
        dispatchCanvas(commands.addNode(node: page), housekeeping: housekeeping)
        return page
    }

    /// A text block is a widget on the current page now — it no longer costs a whole page.
    func addTextBlock() {
        guard let note else { return }
        guard let page = commands.pageForSpawn(state: canvas) else { return }
        let content = NoteContentObjectHelper.shared.createText(
            noteId: note.id,
            positionedAt: Double(noteContents.count),
            text: ""
        )
        let node = commands.widgetIn(
            state: canvas,
            page: page,
            kind: ContentType.text,
            contentId: content.id
        )
        addContent(content)
        texts[node.id] = MasterTextState.companion.of(
            document: RichTextCodec.shared.documentFrom(content: content)
        )
        onCanvasIntent(commands.addNode(node: node))
        onCanvasIntent(commands.setEditing(nodeId: node.id))
    }

    /// A nil content is normal: the attach menu adds an empty table/drawing widget that has no
    /// note content behind it yet. Mirrors MasterEditorViewModel.addWidget on Android.
    func addWidget(kind: ContentType, content: NoteContentModel? = nil) {
        guard note != nil else { return }
        if kind == ContentType.text && content == nil {
            addTextBlock()
            return
        }
        if let content { addContent(content) }
        spawnWidgetNode(kind: kind, contentId: content?.id)
        if kind == ContentType.text { refreshMissingTexts() }
    }

    // 📄 24-Sep-2026 — a new widget lands on the page the user is on, and that page scrolls to show it
    private func spawnWidgetNode(kind: ContentType, contentId: String?, housekeeping: Bool = false) {
        let page = commands.pageForSpawn(state: canvas) ?? addBarePage(housekeeping: housekeeping)
        let node = commands.widgetIn(
            state: canvas,
            page: page,
            kind: kind,
            contentId: contentId
        )
        dispatchCanvas(commands.addNode(node: node), housekeeping: housekeeping)
        if !housekeeping {
            dispatchCanvas(commands.revealEnd(pageId: page.id), housekeeping: true)
        }
    }

    /// 📄 the platform reports what a measured widget's content actually is; a text widget that
    /// grew pushes whatever is under it down, and the page scrolls further — never spills.
    func onWidgetMeasured(nodeId: String, height: CGFloat) {
        guard let node = canvas.document.nodeById(nodeId: nodeId), node.isWidget else { return }
        let measured = Float(height)
        guard measured > 0, abs(node.rect.height - measured) >= Self.measureEpsilon else { return }
        dispatchCanvas(commands.widgetMeasured(nodeId: nodeId, height: measured), housekeeping: true)
    }

    /// Sub-pixel measurement noise must not start a resize/measure ping-pong.
    private static let measureEpsilon: Float = 1.5

    // 🔄 24-Sep-2026 — the content goes first, so the re-order the removal triggers can never save it back
    func deleteNode(nodeId: String) {
        let contentId = canvas.document.nodeById(nodeId: nodeId)?.contentId
        if let contentId { removeContent(id: contentId) }
        onCanvasIntent(commands.removeNode(nodeId: nodeId))
        texts[nodeId] = nil
    }

    func onCapabilityState(_ next: EditorCapabilityState) {
        capabilities = next
    }

    /// Without this a denied camera/mic just does nothing and looks like a broken button.
    func onPermissionDenied(_ type: ContentType) {
        errorMessage = "Allow \(type.name.lowercased()) access in Settings to capture here."
    }

    func requestCapture(_ type: ContentType) {
        onEditorIntent(EditorCommands.shared.captureRequested(type: type))
    }

    func openImportSheet() {
        onEditorIntent(EditorCommands.shared.showImportSheet(visible: true))
    }

    func onCaptured(_ media: CapturedMedia?) {
        guard let note else { return }
        let content = EditorCapture.persist(
            media: media,
            noteId: note.id,
            positionedAt: Double(noteContents.count)
        )
        if let content {
            addWidget(
                kind: content.type,
                content: content
            )
        }
        onEditorIntent(EditorCommands.shared.captureFinished())
    }

    func importFiles(urls: [URL]) {
        guard let note else { return }
        let items = FileImportService.importPicked(
            urls: urls,
            noteId: note.id,
            startPosition: Double(noteContents.count)
        )
        for item in items {
            addWidget(kind: item.type, content: item)
        }
    }

    func importFromLink(_ url: String) {
        guard let note else { return }
        FileImportService.importFromLink(
            url,
            noteId: note.id,
            startPosition: Double(noteContents.count)
        ) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let items):
                for item in items {
                    self.addWidget(
                        kind: item.type,
                        content: item
                    )
                }
            case .noFileFound:
                self.errorMessage = "No file found at this link."
            case .failed(let message):
                self.errorMessage = message
            }
        }
    }

    func askDeleteContent(_ contentId: String) {
        onEditorIntent(EditorCommands.shared.askDeleteContent(contentId: contentId))
    }

    func deleteContent(_ contentId: String) {
        guard let nodeId = canvas.document.nodeForContent(contentId: contentId)?.id else {
            removeContent(id: contentId)
            return
        }
        deleteNode(nodeId: nodeId)
    }

    func renameNode(nodeId: String, name: String) {
        onCanvasIntent(commands.renameNode(nodeId: nodeId, name: name))
        canvasBridge.rename(nodeId: nodeId, name: name)
    }

    // MARK: - Conversion

    // 🔄 24-Sep-2026 — the rebuilt board replaces the old one in a single edit: new rows written, old ones deleted, then the note travels
    func rebuildLayoutFromNote() {
        guard note != nil else { return }
        let page = canvas.document.pages.first
        let document = CanvasPaginator.shared.build(
            contents: noteContents,
            pageWidth: page?.rect.width ?? commands.fittedPageWidth(state: canvas),
            pageHeight: page?.rect.height ?? commands.fittedPageHeight(state: canvas)
        )
        onCanvasIntent(commands.replaceDocument(document: document))
        texts = buildTexts(nodes: document.nodes, contents: textContents)
    }

    func applyCanvasOrderToNote() {
        let reordered = NoteCanvasConverter.shared.reorderContents(
            contents: noteContents,
            document: canvas.document
        )
        onEditorIntent(EditorCommands.shared.addContents(contents: reordered))
    }
}
