import Combine
import SwiftUI
import UIKit
import shared

final class NoteInkAnchors {

    static let space = "noteInk"

    private var rows: [String: CGRect] = [:]

    private weak var scrollView: UIScrollView?

    private(set) lazy var anchors = DrawingAnchors { [weak self] size in
        self?.frames(width: size.width) ?? []
    }

    func attach(_ scroll: UIScrollView) {
        scrollView = scroll
    }

    func update(_ frames: [String: CGRect]) {
        guard frames != rows else { return }
        rows = frames
        anchors.refresh()
    }

    func scroll(by delta: CGFloat) {
        guard let scrollView else { return }
        let inset = scrollView.adjustedContentInset
        let lowest = -inset.top
        let highest = max(lowest, scrollView.contentSize.height + inset.bottom - scrollView.bounds.height)
        let target = min(max(scrollView.contentOffset.y + delta, lowest), highest)
        scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: target), animated: false)
    }

    private func frames(width: CGFloat) -> [DrawAnchorFrame] {
        rows.compactMap { id, rect in
            guard rect.height > 0 else { return nil }
            return DrawCommands.shared.anchor(
                id: id,
                x: 0,
                y: Float(rect.minY),
                width: Float(width),
                height: Float(rect.height)
            )
        }
    }
}

struct NoteInkRowKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]

    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    func noteInkRow(_ id: String) -> some View {
        background(
            GeometryReader { geometry in
                Color.clear.preference(
                    key: NoteInkRowKey.self,
                    value: [id: geometry.frame(in: .named(NoteInkAnchors.space))]
                )
            }
        )
    }
}

final class NoteListNavigator: DrawingNavigator {

    private let ink: NoteInkAnchors

    init(ink: NoteInkAnchors) {
        self.ink = ink
    }

    func navigate(panX: Float, panY: Float, zoom: Float, rotation: Float, focusX: Float, focusY: Float) {
        ink.scroll(by: -CGFloat(panY))
    }

    func end() {}
}

struct NoteDrawingBlock: View {

    let content: NoteContentModel.Drawing
    let session: DrawingSession
    let active: Bool
    let onActivate: () -> Void
    let onDelete: () -> Void

    @Environment(\.colorScheme) private var scheme
    @State private var paperAspect: CGFloat?

    private var aspect: CGFloat {
        if content.isWidget() {
            let notes = DrawNoteContents.shared
            return CGFloat(notes.frameHeight(content: content) / notes.frameWidth(content: content))
        }
        return paperAspect ?? CGFloat(content.aspect())
    }

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        let shape = RoundedRectangle(cornerRadius: 8)
        let navigates = DrawCommands.shared.navigates(state: session.current)
        Color.clear
            .frame(maxWidth: .infinity)
            .aspectRatio(1 / max(aspect, 0.05), contentMode: .fit)
            .overlay {
                DrawingFrame(
                    content: content,
                    session: session,
                    input: active,
                    navigator: active && navigates ? DrawingSessionNavigator(session: session) : nil
                )
            }
            .clipShape(shape)
            .overlay(shape.stroke(active ? palette.accent : palette.divider, lineWidth: active ? 1.5 : 1))
            .overlay(alignment: .topTrailing) {
                if active {
                    Button(action: onDelete) {
                        Image(systemName: DrawingIcons.delete)
                            .foregroundColor(.red)
                            .padding(10)
                    }
                    .accessibilityLabel(Text("Delete drawing"))
                }
            }
            .contentShape(shape)
            .onTapGesture {
                if !active { onActivate() }
            }
            .padding(.vertical, 8)
            .onReceive(session.$state.map { CGFloat(DrawCommands.shared.paperAspect(state: $0)) }.removeDuplicates()) {
                paperAspect = $0
            }
    }
}
