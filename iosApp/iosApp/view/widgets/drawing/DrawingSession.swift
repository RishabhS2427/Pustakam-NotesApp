import Combine
import Foundation
import shared

final class DrawingSession: ObservableObject {

    @Published private(set) var state: DrawEditorState

    let cache: DrawRenderCache = DrawCommands.shared.renderCache()

    private let onDocumentChanged: (DrawEditorState) -> Void

    init(initial: DrawEditorState, onDocumentChanged: @escaping (DrawEditorState) -> Void) {
        self.state = initial
        self.onDocumentChanged = onDocumentChanged
    }

    var current: DrawEditorState { state }

    var capturesTouches: AnyPublisher<Bool, Never> {
        $state
            .map { DrawCommands.shared.capturesTouches(state: $0) }
            .removeDuplicates()
            .eraseToAnyPublisher()
    }

    func dispatch(_ intent: DrawIntent) {
        let before = state
        let next = DrawReducer.shared.reduce(state: before, intent: intent)
        if next === before { return }
        state = next
        if DrawCommands.shared.documentChanged(before: before, next: next) {
            cache.retain(elements: next.document.elements)
            onDocumentChanged(next)
        }
    }

    @discardableResult
    func reload(document: DrawDocument) -> Bool {
        if DrawCommands.shared.isDrawing(state: state) { return false }
        cache.clear()
        state = DrawReducer.shared.reduce(state: state, intent: DrawCommands.shared.load(document: document))
        return true
    }
}

protocol DrawingNavigator: AnyObject {
    func navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float)

    func end()
}

final class DrawingSessionNavigator: DrawingNavigator {

    private let session: DrawingSession

    init(session: DrawingSession) {
        self.session = session
    }

    func navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float) {
        session.dispatch(
            DrawCommands.shared.navigate(panX: panX, panY: panY, zoom: zoom, rotation: rotation, focusX: focusX, focusY: focusY)
        )
    }

    func end() {
        session.dispatch(DrawCommands.shared.navigateEnd())
    }
}

final class DrawingSessions {

    private final class Entry {
        let session: DrawingSession
        var content: NoteContentModel.Drawing
        var written: String
        var stored: Bool
        var pending = false

        init(session: DrawingSession, content: NoteContentModel.Drawing, written: String, stored: Bool) {
            self.session = session
            self.content = content
            self.written = written
            self.stored = stored
        }
    }

    private let onWrite: (NoteContentModel.Drawing) -> Void

    private var entries: [String: Entry] = [:]

    private var activeId: String?

    init(onWrite: @escaping (NoteContentModel.Drawing) -> Void) {
        self.onWrite = onWrite
    }

    func get(_ contentId: String?) -> DrawingSession? {
        guard let contentId else { return nil }
        return entries[contentId]?.session
    }

    func sessionFor(_ content: NoteContentModel.Drawing, readOnly: Bool, stored: Bool = true) -> DrawingSession {
        if let existing = entries[content.id] { return existing.session }
        let notes = DrawNoteContents.shared
        let state: DrawEditorState
        if let template = get(activeId)?.current {
            state = notes.stateCarrying(content: content, previous: template, readOnly: readOnly)
        } else {
            state = notes.stateFor(content: content, author: DrawAuthors.shared.local(), readOnly: readOnly)
        }
        let id = content.id
        let session = DrawingSession(initial: state) { [weak self] next in
            self?.write(id: id, state: next)
        }
        entries[id] = Entry(session: session, content: content, written: content.drawing, stored: stored)
        return session
    }

    func activate(_ contentId: String) {
        if contentId == activeId { return }
        let previous = get(activeId)
        activeId = contentId
        guard let next = get(contentId) else { return }
        if let previous {
            next.dispatch(DrawCommands.shared.carryToolbox(from: previous.current))
        }
    }

    func sync(_ contents: [NoteContentModel]) {
        var drawings: [String: NoteContentModel.Drawing] = [:]
        for case let drawing as NoteContentModel.Drawing in contents {
            drawings[drawing.id] = drawing
        }
        var gone: [String] = []
        for (id, entry) in entries {
            guard let latest = drawings[id] else {
                if entry.stored { gone.append(id) }
                continue
            }
            entry.stored = true
            if latest === entry.content && !entry.pending { continue }
            entry.content = latest
            let payload = latest.drawing
            if payload == entry.written {
                entry.pending = false
            } else if entry.session.reload(document: DrawNoteContents.shared.documentOf(content: latest)) {
                entry.written = payload
                entry.pending = false
            } else {
                entry.pending = true
            }
        }
        gone.forEach { entries.removeValue(forKey: $0) }
    }

    private func write(id: String, state: DrawEditorState) {
        guard let entry = entries[id] else { return }
        let updated = DrawNoteContents.shared.written(content: entry.content, document: state.document)
        entry.content = updated
        entry.written = updated.drawing
        entry.stored = true
        onWrite(updated)
    }
}
