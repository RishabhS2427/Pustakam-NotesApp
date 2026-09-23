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
    private var contentSyncHandle: Closeable?

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

    private func hydrateCanvas(noteId: String) {
        let contents = textContents
        canvasBridge.readCanvas(noteId: noteId) { [weak self] result in
            guard let self else { return }
            switch result {
            case .loading:
                self.isLoading = true
            case .success(let document):
                self.isLoading = false
                var nodes: [CanvasNode] = document?.nodes ?? []
                if nodes.isEmpty {
                    nodes = self.seedNodes(noteId: noteId, contents: contents)
                    self.canvasBridge.saveAll(noteId: noteId, nodes: nodes)
                    // a note built by the "create empty note" path exists only in memory until
                    // now; write it so the canvas has a real parent and refresh() can find it
                    self.saveNote()
                }
                self.readViewport(noteId: noteId, nodes: nodes)
            case .failure(let error):
                self.isLoading = false
                self.errorMessage = error.message
                self.hydratedNoteId = nil
                self.render(nodes: self.seedNodes(noteId: noteId, contents: contents), viewport: nil)
            case .idle:
                break
            }
        }
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
    }

    private func adoptOrphanContents() {
        guard commands.pageForSpawn(state: canvas) != nil else { return }
        for content in noteContents where canvas.document.nodeForContent(contentId: content.id) == nil {
            spawnWidgetNode(kind: content.type, contentId: content.id)
        }
    }

    private func refreshMissingTexts() {
        var updated = texts
        var changed = false
        let byContentId = Dictionary(
            textContents.map { ($0.id, $0) },
            uniquingKeysWith: { _, last in last }
        )
        for node in canvas.document.nodes where node.isText {
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

    private func seedNodes(
        noteId: String,
        contents: [NoteContentModel.TextContent]
    ) -> [CanvasNode] {
        if contents.isEmpty {
            let seed = NoteContentObjectHelper.shared.createText(
                noteId: noteId,
                positionedAt: 0,
                text: ""
            )
            addContent(seed)
            return commands.stackedTextNodes(contentIds: [seed.id])
        }
        return commands.stackedTextNodes(contentIds: contents.map { $0.id })
    }

    private func buildTexts(
        nodes: [CanvasNode],
        contents: [NoteContentModel.TextContent],
        keeping existing: [String: MasterTextState] = [:]
    ) -> [String: MasterTextState] {
        var built: [String: MasterTextState] = [:]
        for node in nodes where node.isText {
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
        let before = canvas
        canvas = CanvasEditorReducer.shared.reduce(state: before, intent: intent)
        persistCanvas(before: before, intent: intent)
    }

    private func persistCanvas(before: CanvasEditorState, intent: CanvasEditorIntent) {
        guard let noteId = note?.id else { return }
        if commands.isEndDrag(intent: intent) {
            guard let dragging = before.draggingNodeId,
                  let node = canvas.document.nodeById(nodeId: dragging) else { return }
            // full upsert, not move(): a drop can also have changed the parent page
            canvasBridge.save(noteId: noteId, node: node)
            for child in canvas.document.descendantsOf(nodeId: node.id) {
                canvasBridge.save(noteId: noteId, node: child)
            }
        } else if let reparentedId = commands.reparentedNodeId(intent: intent) {
            guard let node = canvas.document.nodeById(nodeId: reparentedId) else { return }
            canvasBridge.save(noteId: noteId, node: node)
        } else if let added = commands.addedNode(intent: intent) {
            canvasBridge.save(noteId: noteId, node: added)
        } else if let removedId = commands.removedNodeId(intent: intent) {
            canvasBridge.remove(nodeId: removedId)
        } else if let resizedId = commands.resizedNodeId(intent: intent) {
            guard let node = canvas.document.nodeById(nodeId: resizedId) else { return }
            canvasBridge.resize(nodeId: node.id, width: node.rect.width, height: node.rect.height)
        } else if let fittedId = commands.fittedPageId(state: canvas, intent: intent) {
            if let page = canvas.document.nodeById(nodeId: fittedId),
               page.rect != before.document.nodeById(nodeId: fittedId)?.rect {
                canvasBridge.resize(
                    nodeId: page.id,
                    width: page.rect.width,
                    height: page.rect.height
                )
            }
            if canvas.viewport != before.viewport {
                canvasBridge.saveViewport(noteId: noteId, viewport: canvas.viewport)
            }
        } else if commands.affectsViewport(intent: intent) {
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

    func addPage() {
        guard let note else { return }
        let content = NoteContentObjectHelper.shared.createText(
            noteId: note.id,
            positionedAt: Double(noteContents.count),
            text: ""
        )
        let page = commands.pageNode(state: canvas, contentId: content.id)
        addContent(content)
        texts[page.id] = MasterTextState.companion.of(
            document: RichTextCodec.shared.documentFrom(content: content)
        )
        onCanvasIntent(commands.addNode(node: page))
        onCanvasIntent(commands.selectNode(nodeId: page.id))
    }

    /// A nil content is normal: the attach menu adds an empty table/drawing widget that has no
    /// note content behind it yet. Mirrors MasterEditorViewModel.addWidget on Android.
    func addWidget(kind: ContentType, content: NoteContentModel? = nil) {
        guard note != nil else { return }
        if kind == ContentType.text {
            addPage()
            return
        }
        if let content { addContent(content) }
        spawnWidgetNode(kind: kind, contentId: content?.id)
    }

    /// Split out so captured media can land on the canvas without re-adding its content.
    private func spawnWidgetNode(kind: ContentType, contentId: String?) {
        guard let page = commands.pageForSpawn(state: canvas) else {
            addPage()
            spawnWidgetNode(kind: kind, contentId: contentId)
            return
        }
        let node = commands.widgetIn(
            state: canvas,
            page: page,
            kind: kind,
            contentId: contentId
        )
        onCanvasIntent(commands.addNode(node: node))
    }

    func deleteNode(nodeId: String) {
        let contentId = canvas.document.nodeById(nodeId: nodeId)?.contentId
        onCanvasIntent(commands.removeNode(nodeId: nodeId))
        texts[nodeId] = nil
        guard let contentId else { return }
        removeContent(id: contentId)
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

    func rebuildLayoutFromNote() {
        guard let noteId = note?.id else { return }
        let document = NoteCanvasConverter.shared.toCanvas(contents: noteContents)
        onCanvasIntent(commands.replaceDocument(document: document))
        texts = buildTexts(nodes: document.nodes, contents: textContents)
        canvasBridge.removeAll(noteId: noteId)
        canvasBridge.saveAll(noteId: noteId, nodes: document.nodes)
    }

    func applyCanvasOrderToNote() {
        let reordered = NoteCanvasConverter.shared.reorderContents(
            contents: noteContents,
            document: canvas.document
        )
        onEditorIntent(EditorCommands.shared.addContents(contents: reordered))
    }
}
