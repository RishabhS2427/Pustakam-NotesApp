import Foundation
import shared

final class CanvasBridgeAdapter {

    private let bridge = CanvasBridge()
    private var closeables: [Closeable] = []

    deinit {
        closeables.forEach { $0.close() }
        bridge.dispose()
    }

    // MARK: - Reads

    func readCanvas(noteId: String, onState: @escaping (UiState<CanvasDocument>) -> Void) {
        closeables.append(bridge.readCanvas(
            noteId: noteId,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        ))
    }

    func readViewport(noteId: String, onState: @escaping (UiState<Viewport>) -> Void) {
        closeables.append(bridge.readViewport(
            noteId: noteId,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError:   { onState(.failure($0)) }
        ))
    }

    // MARK: - Writes

    func save(noteId: String, node: CanvasNode, onError: @escaping (BridgeError) -> Void = { _ in }) {
        closeables.append(bridge.save(
            noteId: noteId, node: node, onSuccess: { _ in }, onError: onError
        ))
    }

    func saveAll(
        noteId: String,
        nodes: [CanvasNode],
        onError: @escaping (BridgeError) -> Void = { _ in }
    ) {
        closeables.append(bridge.saveAll(
            noteId: noteId, nodes: nodes, onSuccess: { _ in }, onError: onError
        ))
    }

    func move(
        nodeId: String,
        x: Float,
        y: Float,
        onError: @escaping (BridgeError) -> Void = { _ in }
    ) {
        closeables.append(bridge.move(
            nodeId: nodeId, x: x, y: y, onSuccess: { _ in }, onError: onError
        ))
    }

    func resize(
        nodeId: String,
        width: Float,
        height: Float,
        onError: @escaping (BridgeError) -> Void = { _ in }
    ) {
        closeables.append(bridge.resize(
            nodeId: nodeId, width: width, height: height, onSuccess: { _ in }, onError: onError
        ))
    }

    func rename(
        nodeId: String,
        name: String,
        onError: @escaping (BridgeError) -> Void = { _ in }
    ) {
        closeables.append(bridge.rename(
            nodeId: nodeId, name: name, onSuccess: { _ in }, onError: onError
        ))
    }

    func remove(nodeId: String, onError: @escaping (BridgeError) -> Void = { _ in }) {
        closeables.append(bridge.remove(
            nodeId: nodeId, onSuccess: { _ in }, onError: onError
        ))
    }

    func removeAll(noteId: String, onError: @escaping (BridgeError) -> Void = { _ in }) {
        closeables.append(bridge.removeAll(
            noteId: noteId, onSuccess: { _ in }, onError: onError
        ))
    }

    func saveViewport(
        noteId: String,
        viewport: Viewport,
        onError: @escaping (BridgeError) -> Void = { _ in }
    ) {
        closeables.append(bridge.saveViewport(
            noteId: noteId, viewport: viewport, onSuccess: { _ in }, onError: onError
        ))
    }

    // MARK: - Sync

    // 🔄 24-Sep-2026 — a user's layout edit; onSaved runs once its rows are on disk, which is when the note may be stamped
    func saveEdit(
        noteId: String,
        nodes: [CanvasNode],
        removedIds: [String],
        onSaved: @escaping () -> Void,
        onError: @escaping (BridgeError) -> Void = { _ in }
    ) {
        closeables.append(bridge.saveEdit(
            noteId: noteId, nodes: nodes, removedIds: removedIds, onSuccess: { _ in onSaved() }, onError: onError
        ))
    }

    // 🔄 24-Sep-2026 — onDone runs either way: a failed prune must never keep the board from being read
    func pruneOrphans(onDone: @escaping () -> Void) {
        closeables.append(bridge.pruneOrphans(
            onSuccess: { _ in onDone() }, onError: { _ in onDone() }
        ))
    }

    // 🔄 24-Sep-2026 — another device's canvas for this note, delivered on the main thread
    func observeRemoteCanvas(noteId: String, onChange: @escaping ([CanvasNode]) -> Void) -> Closeable {
        bridge.observeRemoteCanvas(noteId: noteId, onChange: onChange)
    }

    // 📐 24-Sep-2026 — the one-time move of every stored canvas to the compact layout
    func upgradeLayouts(unitScale: Float, maxPaperWidth: Float, onDone: @escaping (Bool) -> Void) {
        closeables.append(bridge.upgradeLayouts(
            unitScale: unitScale,
            maxPaperWidth: maxPaperWidth,
            onSuccess: { _ in onDone(true) },
            onError: { _ in onDone(false) }
        ))
    }
}
