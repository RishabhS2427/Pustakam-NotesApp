import SwiftUI
import shared

/// Height of a page's name bar. Fixed, so a drop can take it off the finger's position exactly.
private let masterPageHeaderHeight: CGFloat = 32

/// The board's own coordinate space — gestures measured in it do not move with a moving page.
private let masterCanvasSpace = "masterCanvas"

/// How long a finger rests before a page or a widget is picked up.
private let masterLiftDelay: Double = 0.4

// 🧱 24-Sep-2026 — a carried widget and the finger carrying it, in board points
private struct LiftedWidget {
    let node: CanvasNode
    let origin: CGPoint
    var translation: CGSize = .zero
    var finger: CGPoint

    var topLeft: CGPoint {
        CGPoint(x: origin.x + translation.width, y: origin.y + translation.height)
    }
}

/// Page scroll state that outlives a page view scrolling off the board, kept out of SwiftUI's
/// diffing: a drop reads the offsets once, and a scroll frame must not re-render the board.
private final class PageScrollBook {
    var offsets: [String: CGPoint] = [:]
    var handledTokens: [String: Int32] = [:]
    // 🧱 24-Sep-2026 — how far each page still has to scroll for a widget carried near its edge
    var carryScroll: [String: CGSize] = [:]
}

private struct MasterPagePlacement: Identifiable {

    let node: CanvasNode
    let title: String
    let frame: CGRect
    let isSelected: Bool

    var id: String { node.id }
}

/// The scaling layer. Pan, zoom and the viewport size are owned here and nowhere else; every
/// page below is positioned and sized from `state.viewport`.
///
/// 📄 23-Sep-2026: the board draws PAPER only. Each page draws its own widgets inside itself,
/// clipped to the paper and scrolled by it, so a widget can never appear outside its page at any
/// zoom. A widget only leaves its page while it is being carried — then the board draws it.
/// Mirrors Android's MasterCanvas.
struct MasterCanvas<NodeContent: View>: View {

    let state: CanvasEditorState
    let onIntent: (CanvasEditorIntent) -> Void
    var onRename: (String, String) -> Void = { _, _ in }
    var onMeasured: (String, CGFloat) -> Void = { _, _ in }
    var keyboardInset: CGFloat = 0
    // 📕 25-Sep-2026 — the note's title, drawn as a hard-cover page; nil or blank draws nothing
    var coverTitle: String? = nil
    @ViewBuilder let nodeContent: (CanvasNode, Bool) -> NodeContent

    @Environment(\.colorScheme) private var scheme
    @State private var liveZoom: CGFloat = 1
    @State private var lastPan: CGSize = .zero
    @State private var lastResize: CGSize = .zero
    @State private var lifted: LiftedWidget?
    @State private var scrollBook = PageScrollBook()
    @State private var carryTick: Int = 0

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    private var viewportScale: Float {
        let scale = state.viewport.scale
        return scale > 0 ? scale : 1
    }

    private var commands: CanvasCommands { CanvasCommands.shared }

    private var permits: CanvasPermits { commands.permitsOf(state: state) }

    var body: some View {
        GeometryReader { geometry in
            canvas(size: geometry.size)
        }
    }

    private func canvas(size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            // background takes the canvas taps; paper and widgets answer their own
            palette.page
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { location in doubleTap(at: location) }
                .onTapGesture { location in singleTap(at: location) }

            // 📕 25-Sep-2026 — never a stored node: the note's title is the only copy of this text
            if let coverTitle, !coverTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                coverLayer(title: coverTitle)
            }

            ForEach(placements()) { placement in
                pageLayer(placement)
            }

            if let lifted {
                liftedLayer(lifted)
            }
        }
        .coordinateSpace(.named(masterCanvasSpace))
        .gesture(canvasGestures)
        .onAppear { reportSize(size) }
        .onChange(of: size) { _, updated in reportSize(updated) }
        // 🧱 24-Sep-2026 — while a widget is carried near a page's edge, that page scrolls the way the finger is going
        .task(id: lifted != nil) { await autoScrollWhileCarrying() }
    }

    /// 📕 25-Sep-2026 — the note's title as a hard cover: centered, no name bar, nothing can be dropped on it.
    private func coverLayer(title: String) -> some View {
        let rect = commands.coverScreenRectOf(state: state).cgRect
        return Text(title)
            .font(.system(size: 22, weight: .bold))
            .multilineTextAlignment(.center)
            .lineLimit(6)
            .foregroundColor(palette.onAccent)
            .padding(24)
            .frame(width: rect.width, height: rect.height)
            .background(palette.accent)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(palette.divider, lineWidth: 1))
            .offset(x: rect.minX, y: rect.minY)
            .allowsHitTesting(false)
    }

    private func placements() -> [MasterPagePlacement] {
        let viewport: Viewport = state.viewport
        let selectedId: String? = state.selectedNodeId
        let pages: [CanvasNode] = state.document.pages

        var order: [String: Int32] = [:]
        for (position, page) in pages.enumerated() {
            order[page.id] = Int32(position)
        }

        return state.visibleNodes.map { page in
            let rect: CanvasRect = commands.screenRectOf(
                document: state.document,
                node: page,
                viewport: viewport
            )
            return MasterPagePlacement(
                node: page,
                title: page.displayName(fallbackIndex: order[page.id] ?? 0),
                frame: rect.cgRect,
                isSelected: page.id == selectedId
            )
        }
    }

    @ViewBuilder
    private func pageLayer(_ placement: MasterPagePlacement) -> some View {
        let page: CanvasNode = placement.node
        let frame: CGRect = placement.frame

        MasterPageView(
            page: page,
            title: placement.title,
            frame: frame,
            isSelected: placement.isSelected,
            state: state,
            palette: palette,
            keyboardInset: keyboardInset,
            scrollBook: scrollBook,
            liftedId: lifted?.node.id,
            carryTick: carryTick,
            onIntent: onIntent,
            onRename: onRename,
            onMeasured: onMeasured,
            onLift: { widget, origin, finger in startLift(widget, origin: origin, finger: finger) },
            onLiftMove: { translation, finger in moveLift(translation: translation, finger: finger) },
            onLiftEnd: { completed in endLift(completed: completed) },
            nodeContent: nodeContent
        )
        .overlay(alignment: .bottomTrailing) {
            if placement.isSelected && permits.canResizeNode && !page.locked {
                resizeHandle(for: page)
            }
        }
        .offset(x: frame.minX, y: frame.minY)
    }

    /// The carried widget, drawn above every page while the finger moves it.
    private func liftedLayer(_ carried: LiftedWidget) -> some View {
        let scale = CGFloat(viewportScale)
        let topLeft = carried.topLeft
        return ScaledWidget(widget: carried.node, scale: scale, onMeasured: nil) {
            nodeContent(carried.node, false)
        }
        .frame(
            width: CGFloat(carried.node.rect.width) * scale,
            height: CGFloat(carried.node.rect.height) * scale,
            alignment: .top
        )
        .background(palette.page)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .shadow(color: .black.opacity(0.25), radius: 8)
        .offset(x: topLeft.x, y: topLeft.y)
        .allowsHitTesting(false)
    }

    private func startLift(_ widget: CanvasNode, origin: CGPoint, finger: CGPoint) -> Bool {
        guard commands.canLift(state: state) else { return false }
        onIntent(commands.beginDrag(nodeId: widget.id))
        lifted = LiftedWidget(node: widget, origin: origin, finger: finger)
        return true
    }

    private func moveLift(translation: CGSize, finger: CGPoint) {
        guard var carried = lifted else { return }
        carried.translation = translation
        carried.finger = finger
        lifted = carried
    }

    /// Released over a page, the widget lands where it was let go; anywhere else it goes home.
    private func endLift(completed: Bool) {
        guard let carried = lifted else { return }
        lifted = nil
        scrollBook.carryScroll.removeAll()
        let scale = CGFloat(viewportScale)
        let topLeft = carried.topLeft
        let target: CanvasNode? = completed
            ? commands.dropTargetAt(
                state: state,
                screenX: Float(topLeft.x + CGFloat(carried.node.rect.width) * scale / 2),
                screenY: Float(topLeft.y + CGFloat(carried.node.rect.height) * scale / 2)
            )
            : nil
        let offset: CGPoint = target.flatMap { scrollBook.offsets[$0.id] } ?? .zero
        onIntent(
            commands.dropWidget(
                state: state,
                widgetId: carried.node.id,
                target: target,
                screenX: Float(topLeft.x),
                screenY: Float(topLeft.y),
                scrollX: Float(offset.x),
                scrollY: Float(offset.y),
                headerHeight: Float(masterPageHeaderHeight)
            )
        )
    }

    // 🧱 24-Sep-2026 — each frame the page under the carrying finger is asked to scroll by the step the shared rule gives
    @MainActor
    private func autoScrollWhileCarrying() async {
        while !Task.isCancelled, let carried = lifted {
            let finger = carried.finger
            if let target = commands.dropTargetAt(
                state: state,
                screenX: Float(finger.x),
                screenY: Float(finger.y)
            ) {
                let screen: CanvasRect = commands.screenRectOf(
                    document: state.document,
                    node: target,
                    viewport: state.viewport
                )
                let header = Float(masterPageHeaderHeight)
                let stepY = commands.autoScrollStep(
                    position: Float(finger.y),
                    start: screen.y + header,
                    length: screen.height - header
                )
                let stepX = commands.autoScrollStep(
                    position: Float(finger.x),
                    start: screen.x,
                    length: screen.width
                )
                if stepX != 0 || stepY != 0 {
                    let pending = scrollBook.carryScroll[target.id] ?? .zero
                    scrollBook.carryScroll[target.id] = CGSize(
                        width: pending.width + CGFloat(stepX),
                        height: pending.height + CGFloat(stepY)
                    )
                    carryTick &+= 1
                }
            }
            try? await Task.sleep(nanoseconds: 16_000_000)
        }
    }

    private func resizeHandle(for node: CanvasNode) -> some View {
        RoundedRectangle(cornerRadius: 4)
            .fill(palette.accent)
            .frame(width: 22, height: 22)
            .padding(2)
            .gesture(
                DragGesture(minimumDistance: 1)
                    .onChanged { value in
                        if lastResize == .zero {
                            onIntent(commands.beginResize(nodeId: node.id))
                        }
                        let deltaX = value.translation.width - lastResize.width
                        let deltaY = value.translation.height - lastResize.height
                        lastResize = value.translation
                        guard let intent = commands.resizedTo(
                            state: state,
                            nodeId: node.id,
                            deltaXPx: Float(deltaX),
                            deltaYPx: Float(deltaY)
                        ) else { return }
                        onIntent(intent)
                    }
                    .onEnded { _ in
                        lastResize = .zero
                        onIntent(commands.endResize())
                    }
            )
    }

    /// GeometryReader reports during layout, so publishing the new viewport straight away
    /// mutates state inside the update pass. Skip no-op sizes, defer the rest by one tick.
    private func reportSize(_ size: CGSize) {
        let width = Float(size.width)
        let height = Float(size.height)
        guard width > 0, height > 0 else { return }
        guard state.viewport.widthPx != width || state.viewport.heightPx != height else { return }
        DispatchQueue.main.async {
            onIntent(commands.viewportResized(width: width, height: height))
        }
    }

    private func singleTap(at location: CGPoint) {
        let screenX = Float(location.x)
        let screenY = Float(location.y)
        onIntent(commands.selectAt(screenX: screenX, screenY: screenY))
    }

    private func doubleTap(at location: CGPoint) {
        let point: CGPoint = state.viewport.canvasPoint(of: location)
        let canvasX = Float(point.x)
        let canvasY = Float(point.y)
        if let node = state.document.hitTest(canvasX: canvasX, canvasY: canvasY) {
            onIntent(commands.setEditing(nodeId: node.id))
        } else if commands.isEditing(state: state) {
            onIntent(commands.exitEditing())
        } else {
            onIntent(commands.zoomToFit())
        }
    }

    private var canvasGestures: some Gesture {
        SimultaneousGesture(magnifyGesture, panGesture)
    }

    private var magnifyGesture: some Gesture {
        MagnificationGesture()
            .onChanged { value in
                let factor: CGFloat = value / liveZoom
                liveZoom = value
                let focusX = Float(state.viewport.widthPx / 2)
                let focusY = Float(state.viewport.heightPx / 2)
                onIntent(commands.zoom(factor: Float(factor), focusX: focusX, focusY: focusY))
            }
            .onEnded { _ in liveZoom = 1 }
    }

    private var panGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                guard permits.canPan else { return }
                let deltaX: CGFloat = value.translation.width - lastPan.width
                let deltaY: CGFloat = value.translation.height - lastPan.height
                lastPan = value.translation
                onIntent(commands.pan(deltaX: Float(deltaX), deltaY: Float(deltaY)))
            }
            .onEnded { _ in lastPan = .zero }
    }
}

// 🧱 24-Sep-2026 — text reflows at the zoom; every other widget is laid out at its own size and zoomed like a picture, so a pinch never changes its height
private struct ScaledWidget<Content: View>: View {

    let widget: CanvasNode
    let scale: CGFloat
    let onMeasured: ((CGFloat) -> Void)?
    @ViewBuilder let content: () -> Content

    var body: some View {
        let width = CGFloat(widget.rect.width)
        let height = CGFloat(widget.rect.height)
        if widget.isTextWidget {
            content()
                .frame(width: width * scale, alignment: .top)
                .fixedSize(horizontal: false, vertical: true)
                .background(measurer { measured in onMeasured?(measured / scale) })
        } else {
            laidOut(width: width, height: height)
                .scaleEffect(scale, anchor: .topLeading)
                .frame(width: width * scale, height: height * scale, alignment: .topLeading)
                .clipped()
        }
    }

    @ViewBuilder
    private func laidOut(width: CGFloat, height: CGFloat) -> some View {
        if widget.isMeasured {
            content()
                .frame(width: width, alignment: .top)
                .fixedSize(horizontal: false, vertical: true)
                .background(measurer { measured in onMeasured?(measured) })
                .frame(width: width, height: height, alignment: .top)
        } else {
            content()
                .frame(width: width, height: height, alignment: .top)
        }
    }

    private func measurer(_ report: @escaping (CGFloat) -> Void) -> some View {
        GeometryReader { proxy in
            Color.clear
                .onAppear { report(proxy.size.height) }
                .onChange(of: proxy.size.height) { _, measured in report(measured) }
        }
    }
}

/// One sheet of paper: a name bar, then a viewport that scrolls vertically AND horizontally over
/// the page's content. Widgets keep their size and place on that content whatever the paper does
/// — when the paper shrinks they are simply scrolled to — and nothing is drawn outside it.
private struct MasterPageView<NodeContent: View>: View {

    let page: CanvasNode
    let title: String
    let frame: CGRect
    let isSelected: Bool
    let state: CanvasEditorState
    let palette: SmartTextPalette
    let keyboardInset: CGFloat
    let scrollBook: PageScrollBook
    let liftedId: String?
    let carryTick: Int
    let onIntent: (CanvasEditorIntent) -> Void
    let onRename: (String, String) -> Void
    let onMeasured: (String, CGFloat) -> Void
    let onLift: (CanvasNode, CGPoint, CGPoint) -> Bool
    let onLiftMove: (CGSize, CGPoint) -> Void
    let onLiftEnd: (Bool) -> Void
    @ViewBuilder let nodeContent: (CanvasNode, Bool) -> NodeContent

    @State private var position = ScrollPosition(edge: .top)
    @State private var carrying = false
    @State private var carryingPage = false
    @State private var lastPageDrag: CGSize = .zero
    // 🧱 24-Sep-2026 — true while a long-press is held; SwiftUI resets it even when it cancels a gesture without calling onEnded
    @GestureState private var liftHeld = false
    @GestureState private var paperHeld = false

    private var commands: CanvasCommands { CanvasCommands.shared }

    private var scale: CGFloat {
        let scale = state.viewport.scale
        return CGFloat(scale > 0 ? scale : 1)
    }

    var body: some View {
        let widgets: [CanvasNode] = commands.widgetsOnPage(state: state, pageId: page.id)
        let extent: CanvasRect = commands.contentExtentOf(state: state, pageId: page.id)
        let viewportHeight: CGFloat = max(frame.height - masterPageHeaderHeight, 0)
        let editingHere: Bool = widgets.contains { $0.id == state.editingNodeId }
        // while typing on this page there must be room to scroll the caret clear of the keyboard
        let keyboardRoom: CGFloat =
            editingHere && keyboardInset > 0 ? keyboardInset + viewportHeight / 3 : 0
        let contentWidth: CGFloat = max(frame.width, CGFloat(extent.width) * scale)
        let contentHeight: CGFloat =
            max(viewportHeight, CGFloat(extent.height) * scale) + keyboardRoom
        let token: Int32 = commands.scrollTokenFor(state: state, pageId: page.id)

        return VStack(spacing: 0) {
            MasterNodeNameBar(
                title: title,
                initialName: page.name,
                isSelected: isSelected,
                palette: palette,
                onRename: { newName in onRename(page.id, newName) }
            )
            .frame(height: masterPageHeaderHeight)
            .contentShape(Rectangle())
            .gesture(paperGesture)

            ScrollView([.vertical, .horizontal], showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    // the paper sits UNDER the widgets, so a touch on a widget never reaches it
                    // — only bare paper focuses or carries the page
                    palette.page
                        .frame(width: contentWidth, height: contentHeight)
                        .contentShape(Rectangle())
                        .gesture(paperGesture)

                    ForEach(widgets) { widget in
                        widgetView(widget)
                    }
                }
                .frame(width: contentWidth, height: contentHeight, alignment: .topLeading)
            }
            .scrollPosition($position)
            .scrollDisabled(carrying || carryingPage)
            .onScrollGeometryChange(for: CGPoint.self) { geometry in
                geometry.contentOffset
            } action: { _, offset in
                scrollBook.offsets[page.id] = offset
            }
        }
        .frame(width: frame.width, height: frame.height)
        .background(RoundedRectangle(cornerRadius: 8).fill(palette.page))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(border)
        // a page view that comes back onto the board picks up where its scroll was
        .onAppear { position.scrollTo(point: scrollBook.offsets[page.id] ?? .zero) }
        // a tap on paper asks for its end; each request is acted on once, after the fit lands —
        // including one already waiting when this view is created (the first open)
        .onChange(of: token, initial: true) { _, latest in
            scrollToEnd(latest, bottom: max(contentHeight - viewportHeight, 0))
        }
        .onChange(of: carryTick) { _, _ in
            applyCarryScroll(
                maxX: max(contentWidth - frame.width, 0),
                maxY: max(contentHeight - viewportHeight, 0)
            )
        }
        // 🧱 24-Sep-2026 — a carry SwiftUI cancelled puts the widget back instead of leaving the board locked
        .onChange(of: liftHeld) { _, held in
            guard !held else { return }
            DispatchQueue.main.async {
                guard carrying else { return }
                carrying = false
                onLiftEnd(false)
            }
        }
        .onChange(of: paperHeld) { _, held in
            guard !held else { return }
            DispatchQueue.main.async {
                guard carryingPage else { return }
                carryingPage = false
                lastPageDrag = .zero
                onIntent(commands.endDrag())
            }
        }
    }

    private var border: some View {
        let color: Color = isSelected ? palette.accent : palette.divider
        let width: CGFloat = isSelected ? 1.5 : 1.0
        return RoundedRectangle(cornerRadius: 8).stroke(color, lineWidth: width)
    }

    /// One widget on the page's content, at its own fixed size and place.
    @ViewBuilder
    private func widgetView(_ widget: CanvasNode) -> some View {
        let isEditing: Bool = widget.id == state.editingNodeId

        ScaledWidget(
            widget: widget,
            scale: scale,
            onMeasured: { measured in report(widget, measured: measured) }
        ) {
            nodeContent(widget, isEditing)
        }
        .overlay {
            if widget.isTextWidget && !isEditing {
                // until it has the caret, a text widget is a block: tap starts editing it,
                // long-press carries it — the text view underneath never sees either touch
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        liftGesture(widget).exclusively(
                            before: TapGesture().onEnded {
                                onIntent(commands.setEditing(nodeId: widget.id))
                            }
                        )
                    )
            }
        }
        // text is lifted through its shield above; every other widget lifts from here
        .simultaneousGesture(
            liftGesture(widget),
            including: widget.isTextWidget ? .subviews : .all
        )
        .opacity(widget.id == liftedId ? 0 : 1)
        .offset(x: CGFloat(widget.rect.x) * scale, y: CGFloat(widget.rect.y) * scale)
    }

    /// Where a widget's top-left sits on the board right now, past the name bar and the scroll.
    private func origin(of widget: CanvasNode) -> CGPoint {
        let offset: CGPoint = scrollBook.offsets[page.id] ?? .zero
        return CGPoint(
            x: frame.minX + CGFloat(widget.rect.x) * scale - offset.x,
            y: frame.minY + masterPageHeaderHeight + CGFloat(widget.rect.y) * scale - offset.y
        )
    }

    /// Long-press picks the widget up; the finger then carries it, in board coordinates.
    private func liftGesture(_ widget: CanvasNode) -> some Gesture {
        LongPressGesture(minimumDuration: masterLiftDelay)
            .sequenced(
                before: DragGesture(minimumDistance: 0, coordinateSpace: .named(masterCanvasSpace))
            )
            .updating($liftHeld) { value, held, _ in
                if case .second(true, _) = value { held = true }
            }
            .onChanged { value in
                guard case .second(true, let drag) = value else { return }
                if !carrying {
                    let start = origin(of: widget)
                    carrying = onLift(widget, start, drag?.location ?? start)
                }
                if carrying, let drag {
                    onLiftMove(drag.translation, drag.location)
                }
            }
            .onEnded { value in
                guard carrying else { return }
                carrying = false
                if case .second(true, _) = value {
                    onLiftEnd(true)
                } else {
                    onLiftEnd(false)
                }
            }
    }

    /// Bare paper: a tap focuses the page, a long-press picks the page up and carries it.
    private var paperGesture: some Gesture {
        LongPressGesture(minimumDuration: masterLiftDelay)
            .sequenced(
                before: DragGesture(minimumDistance: 0, coordinateSpace: .named(masterCanvasSpace))
            )
            .updating($paperHeld) { value, held, _ in
                if case .second(true, _) = value { held = true }
            }
            .onChanged { value in
                guard case .second(true, let drag) = value else { return }
                if !carryingPage {
                    guard commands.canLift(state: state) else { return }
                    onIntent(commands.beginDrag(nodeId: page.id))
                    carryingPage = true
                    lastPageDrag = .zero
                }
                guard let drag else { return }
                let deltaX: CGFloat = drag.translation.width - lastPageDrag.width
                let deltaY: CGFloat = drag.translation.height - lastPageDrag.height
                lastPageDrag = drag.translation
                onIntent(commands.dragBy(deltaX: Float(deltaX), deltaY: Float(deltaY)))
            }
            .onEnded { _ in
                lastPageDrag = .zero
                guard carryingPage else { return }
                carryingPage = false
                onIntent(commands.endDrag())
            }
            .exclusively(
                before: TapGesture().onEnded { onIntent(commands.focusPage(pageId: page.id)) }
            )
    }

    /// A measured widget reports its true height, in canvas units, so its page can scroll to it.
    private func report(_ widget: CanvasNode, measured: CGFloat) {
        guard widget.isMeasured, measured > 0 else { return }
        onMeasured(widget.id, measured)
    }

    private func scrollToEnd(_ token: Int32, bottom: CGFloat) {
        guard token > (scrollBook.handledTokens[page.id] ?? 0) else { return }
        scrollBook.handledTokens[page.id] = token
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.25)) {
                position.scrollTo(point: CGPoint(x: 0, y: bottom))
            }
        }
    }

    // 🧱 24-Sep-2026 — the board asked this page to scroll for a carried widget; never past either end of its content
    private func applyCarryScroll(maxX: CGFloat, maxY: CGFloat) {
        guard let delta = scrollBook.carryScroll.removeValue(forKey: page.id) else { return }
        let current: CGPoint = scrollBook.offsets[page.id] ?? .zero
        let next = CGPoint(
            x: min(max(current.x + delta.width, 0), maxX),
            y: min(max(current.y + delta.height, 0), maxY)
        )
        guard next != current else { return }
        scrollBook.offsets[page.id] = next
        position.scrollTo(point: next)
    }
}

private struct MasterNodeNameBar: View {

    let title: String
    let initialName: String
    let isSelected: Bool
    let palette: SmartTextPalette
    let onRename: (String) -> Void

    @State private var editing = false
    @State private var draft = ""

    var body: some View {
        content
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background(isSelected ? palette.accentSoft : palette.surface)
    }

    @ViewBuilder
    private var content: some View {
        if editing {
            nameField
        } else {
            nameLabel
        }
    }

    private var nameField: some View {
        TextField("Name", text: $draft)
            .font(.system(size: 12))
            .foregroundColor(palette.onSurface)
            .submitLabel(.done)
            .onSubmit { commit() }
    }

    // only the title renames; the rest of the bar is paper — tap focuses, long-press carries
    private var nameLabel: some View {
        Text(title)
            .font(.system(size: 12))
            .foregroundColor(isSelected ? palette.accent : palette.onSurfaceMuted)
            .contentShape(Rectangle())
            .onTapGesture { beginEditing() }
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func beginEditing() {
        draft = initialName
        editing = true
    }

    private func commit() {
        editing = false
        onRename(draft.trimmingCharacters(in: .whitespaces))
    }
}
