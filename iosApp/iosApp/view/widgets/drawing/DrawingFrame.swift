import SwiftUI
import UIKit
import shared

struct DrawingFrame: View {

    let content: NoteContentModel.Drawing
    let session: DrawingSession
    var input: Bool = false
    var navigator: DrawingNavigator? = nil

    var body: some View {
        GeometryReader { proxy in
            if content.isWidget() {
                widget(in: proxy.size)
            } else {
                DrawingCanvas(session: session, navigator: navigator, input: input)
            }
        }
    }

    private func widget(in size: CGSize) -> some View {
        let notes = DrawNoteContents.shared
        let width = CGFloat(notes.frameWidth(content: content))
        let height = CGFloat(notes.frameHeight(content: content))
        let scale = CGFloat(
            notes.fitScale(frameWidth: Float(width), frameHeight: Float(height), width: Float(size.width), height: Float(size.height))
        )
        return DrawingCanvas(session: session, navigator: navigator, input: input, zoom: scale)
            .frame(width: width, height: height)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: width * scale, height: height * scale, alignment: .topLeading)
            .clipped()
            .frame(width: size.width, height: size.height)
    }
}

struct DrawingPageLayer: View {

    let session: DrawingSession
    let anchorId: String
    let active: Bool

    var body: some View {
        DrawingCanvas(session: session, anchors: .page(anchorId), input: active)
    }
}

struct DrawingScrollFinder: UIViewRepresentable {

    let onFound: (UIScrollView) -> Void

    func makeUIView(context: Context) -> DrawingScrollProbe {
        DrawingScrollProbe(onFound: onFound)
    }

    func updateUIView(_ view: DrawingScrollProbe, context: Context) {
        view.onFound = onFound
        view.search()
    }
}

final class DrawingScrollProbe: UIView {

    var onFound: (UIScrollView) -> Void

    init(onFound: @escaping (UIScrollView) -> Void) {
        self.onFound = onFound
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        backgroundColor = .clear
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        search()
    }

    func search() {
        var view = superview
        while let current = view {
            if let scroll = current as? UIScrollView {
                onFound(scroll)
                return
            }
            view = current.superview
        }
    }
}
