import Combine
import Foundation
import shared

final class DrawingHost: ObservableObject {

    @Published private(set) var target: String?

    @Published private(set) var overlay: DrawingSession?

    private let contents: () -> [NoteContentModel]

    private let noteId: () -> String?

    private let documentId: () -> String?

    private let sessions: DrawingSessions

    private var overlayDraft: NoteContentModel.Drawing?

    init(
        contents: @escaping () -> [NoteContentModel],
        noteId: @escaping () -> String?,
        documentId: @escaping () -> String?,
        onWrite: @escaping (NoteContentModel.Drawing) -> Void
    ) {
        self.contents = contents
        self.noteId = noteId
        self.documentId = documentId
        self.sessions = DrawingSessions(onWrite: onWrite)
    }

    func sync(_ latest: [NoteContentModel]) {
        sessions.sync(latest)
        publishOverlay(overlaySession(in: latest, create: false))
        if target != nil && sessions.get(target) == nil { target = nil }
    }

    func session(_ content: NoteContentModel.Drawing) -> DrawingSession {
        sessions.sessionFor(content, readOnly: false)
    }

    func active() -> DrawingSession? { sessions.get(target) }

    func overlayId() -> String? { (storedOverlay(contents()) ?? overlayDraft)?.id }

    func isOverlayActive() -> Bool { target != nil && target == overlayId() }

    func overlaySession(create: Bool) -> DrawingSession? {
        overlaySession(in: contents(), create: create)
    }

    func toggleOverlay() {
        if isOverlayActive() {
            stop()
            return
        }
        publishOverlay(overlaySession(create: true))
        if let id = overlayId() { start(id) }
    }

    func open(_ content: NoteContentModel.Drawing) {
        _ = session(content)
        start(content.id)
    }

    func start(_ contentId: String) {
        sessions.activate(contentId)
        target = contentId
    }

    func stop() {
        target = nil
    }

    private func publishOverlay(_ next: DrawingSession?) {
        if next !== overlay { overlay = next }
    }

    private func overlaySession(in current: [NoteContentModel], create: Bool) -> DrawingSession? {
        if let stored = storedOverlay(current) { return session(stored) }
        let draft = overlayDraft ?? (create ? newOverlayDraft(current) : nil)
        return draft.map { sessions.sessionFor($0, readOnly: false, stored: false) }
    }

    private func storedOverlay(_ current: [NoteContentModel]) -> NoteContentModel.Drawing? {
        guard let document = documentId() else { return DrawNoteContents.shared.overlayOf(contents: current) }
        return DrawNoteContents.shared.annotationOf(contents: current, targetId: document)
    }

    private func newOverlayDraft(_ current: [NoteContentModel]) -> NoteContentModel.Drawing? {
        guard let id = noteId() else { return nil }
        let notes = DrawNoteContents.shared
        let position = notes.nextPosition(contents: current)
        let draft: NoteContentModel.Drawing
        if let document = documentId() {
            draft = notes.createAnnotation(noteId: id, position: position, targetId: document)
        } else {
            draft = notes.createOverlay(noteId: id, position: position)
        }
        overlayDraft = draft
        return draft
    }
}
