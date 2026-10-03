import Combine
import SwiftUI
import UIKit
import shared

final class ImageAnnotationViewModel: ObservableObject {

    private static let maxEdge: CGFloat = 2048

    @Published private(set) var target: NoteContentModel.MediaContent?
    @Published private(set) var base: UIImage?
    @Published private(set) var saving = false

    let annotations = BookReaderAnnotations()

    private var mediaId: String?

    private var changes = Set<AnyCancellable>()

    init() {
        annotations.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &changes)
        annotations.onContents = { [weak self] in self?.resolve($0) }
    }

    var drawing: DrawingHost { annotations.drawing }

    var isDrawing: Bool { annotations.annotating }

    var canDraw: Bool { target != nil && base != nil }

    func open(noteId: String?, mediaId: String?) {
        guard let noteId, !noteId.isEmpty, let mediaId, !mediaId.isEmpty, self.mediaId != mediaId else { return }
        self.mediaId = mediaId
        annotations.followNote(noteId: noteId, documentId: mediaId)
    }

    func toggle() {
        guard base != nil else { return }
        drawing.toggleOverlay()
    }

    func finish() {
        drawing.stop()
        annotations.flushAnnotation()
        guard let original = target, let base else { return }
        let notes = DrawNoteContents.shared
        let contents = annotations.contents
        let annotation = notes.annotationOf(contents: contents, targetId: original.id)
        guard notes.hasInk(content: annotation, imageId: original.id) else {
            if let previous = notes.editedCopyOf(contents: contents, imageId: original.id) { discard(previous) }
            return
        }
        saving = true
        let width = base.size.width * base.scale
        let height = base.size.height * base.scale
        DispatchQueue.global(qos: .userInitiated).async {
            let entries = notes.inkFrame(content: annotation, imageId: original.id, width: Float(width), height: Float(height))
            let rendered = DrawingRender.image(base: base, entries: entries)
            let saved = EditorCapture.persist(media: .image(rendered), noteId: original.noteId, positionedAt: original.position)
            DispatchQueue.main.async {
                if let path = saved?.localPath {
                    self.replaceCopy(original: original, path: path, width: Int32(width), height: Int32(height))
                }
                self.saving = false
            }
        }
    }

    private func replaceCopy(original: NoteContentModel.MediaContent, path: String, width: Int32, height: Int32) {
        let notes = DrawNoteContents.shared
        let contents = annotations.contents
        let previous = notes.editedCopyOf(contents: contents, imageId: original.id)
        annotations.write(notes.editedCopy(contents: contents, original: original, localPath: path, width: width, height: height))
        if let previous { discard(previous) }
    }

    private func discard(_ copy: NoteContentModel.MediaContent) {
        annotations.remove(contentId: copy.id)
        deleteFilesLater(NoteFiles.shared.pathsOf(content: copy))
    }

    private func resolve(_ contents: [NoteContentModel]) {
        guard let mediaId, annotations.writable, let found = DrawNoteContents.shared.editTargetOf(contents: contents, mediaId: mediaId) else { return }
        let changed = found.id != target?.id
        target = found
        annotations.target(documentId: found.id)
        guard changed || base == nil else { return }
        let path = found.getMediaUrl()
        guard !path.isEmpty else { return }
        DispatchQueue.global(qos: .userInitiated).async {
            let image = UIImage(contentsOfFile: path).map(Self.fitted)
            DispatchQueue.main.async { [weak self] in self?.base = image }
        }
    }

    private static func fitted(_ image: UIImage) -> UIImage {
        let pixels = CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
        let ratio = min(1, maxEdge / max(pixels.width, pixels.height, 1))
        let size = CGSize(width: (pixels.width * ratio).rounded(), height: (pixels.height * ratio).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
