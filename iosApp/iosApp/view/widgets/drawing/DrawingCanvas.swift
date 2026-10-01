import Combine
import SwiftUI
import UIKit
import shared

final class DrawingAnchors {

    private let read: (CGSize) -> [DrawAnchorFrame]

    private var listeners: [ObjectIdentifier: () -> Void] = [:]

    init(_ read: @escaping (CGSize) -> [DrawAnchorFrame]) {
        self.read = read
    }

    static func page(_ anchorId: String) -> DrawingAnchors {
        DrawingAnchors { size in
            [DrawCommands.shared.anchor(id: anchorId, x: 0, y: 0, width: Float(size.width), height: Float(size.height))]
        }
    }

    func frames(in size: CGSize) -> [DrawAnchorFrame] { read(size) }

    func refresh() {
        listeners.values.forEach { $0() }
    }

    func listen(_ owner: AnyObject, _ listener: @escaping () -> Void) {
        listeners[ObjectIdentifier(owner)] = listener
    }

    func forget(_ owner: AnyObject) {
        listeners.removeValue(forKey: ObjectIdentifier(owner))
    }
}

struct DrawingCanvas: UIViewRepresentable {

    let session: DrawingSession
    var anchors: DrawingAnchors? = nil
    var navigator: DrawingNavigator? = nil
    var input: Bool = false
    var zoom: CGFloat = 1

    func makeUIView(context: Context) -> DrawingCanvasView {
        let view = DrawingCanvasView()
        view.configure(session: session, anchors: anchors, navigator: navigator, input: input, zoom: zoom)
        return view
    }

    func updateUIView(_ view: DrawingCanvasView, context: Context) {
        view.configure(session: session, anchors: anchors, navigator: navigator, input: input, zoom: zoom)
    }
}

struct DrawingEntries: View {

    let entries: (Float, Float) -> [DrawRenderEntry]

    var body: some View {
        Canvas { context, size in
            context.withCGContext { cg in
                DrawingRender.draw(entries(Float(size.width), Float(size.height)), in: cg, paths: nil)
            }
        }
    }
}

final class DrawingCanvasView: UIView {

    private static let maxPixels: CGFloat = 4096

    private let ink = DrawingInkView()

    private let committed = DrawingCommittedLayer()

    private let paths = DrawingPathCache()

    private var session: DrawingSession?

    private var subscription: AnyCancellable?

    private var anchors: DrawingAnchors?

    private var navigator: DrawingNavigator?

    private var inputEnabled = false

    private var zoom: CGFloat = 1

    private var paper: DrawPaperView?

    private var reportedSize: CGSize = .zero

    private var strokeTouch: UITouch?

    private var navigating = false

    private var relaxed: [DrawingRelaxedScroll] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isMultipleTouchEnabled = true
        contentMode = .redraw
        ink.owner = self
        addSubview(ink)
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    deinit {
        anchors?.forget(self)
        relaxed.forEach { $0.restore() }
    }

    func configure(
        session: DrawingSession,
        anchors: DrawingAnchors?,
        navigator: DrawingNavigator?,
        input: Bool,
        zoom: CGFloat
    ) {
        if self.session !== session {
            cancelGesture()
            self.session = session
            paper = nil
            reportedSize = .zero
            committed.clear()
            subscription = session.$state.sink { [weak self] state in self?.stateChanged(state) }
            setNeedsLayout()
        }
        if self.anchors !== anchors {
            self.anchors?.forget(self)
            self.anchors = anchors
            anchors?.listen(self) { [weak self] in self?.ink.setNeedsDisplay() }
        }
        self.navigator = navigator
        if inputEnabled != input {
            inputEnabled = input
            if !input { cancelGesture() }
            relaxScrolls()
        }
        if self.zoom != zoom {
            self.zoom = zoom
            updateResolution()
        }
        ink.setNeedsDisplay()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        ink.frame = bounds
        updateResolution()
        let size = bounds.size
        guard size.width > 0, size.height > 0, size != reportedSize else { return }
        reportedSize = size
        DispatchQueue.main.async { [weak self] in
            guard let self, self.reportedSize == size else { return }
            self.session?.dispatch(DrawCommands.shared.resize(width: Float(size.width), height: Float(size.height)))
        }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateResolution()
        relaxScrolls()
        if window == nil { cancelGesture() }
    }

    override func draw(_ rect: CGRect) {
        guard let paper, let context = UIGraphicsGetCurrentContext() else { return }
        DrawingRender.draw(DrawCommands.shared.paperFrameOf(view: paper), in: context, paths: nil)
    }

    fileprivate func drawInk(in rect: CGRect) {
        guard rect.width > 0, rect.height > 0, let session, let context = UIGraphicsGetCurrentContext() else { return }
        let commands = DrawCommands.shared
        let state = session.current
        let frames = currentAnchors()
        if commands.cachesInk(zoom: Float(zoom)) {
            let key = commands.committedKey(state: state, anchors: frames, width: Float(rect.width), height: Float(rect.height))
            let image = committed.image(for: key, size: rect.size, scale: contentScaleFactor) { cg in
                DrawingRender.draw(commands.committedFrame(state: state, anchors: frames, cache: session.cache), in: cg, paths: paths)
            }
            image.draw(in: rect)
        } else {
            DrawingRender.draw(commands.committedFrame(state: state, anchors: frames, cache: session.cache), in: context, paths: paths)
        }
        DrawingRender.draw(commands.previewFrame(state: state, anchors: frames), in: context, paths: nil)
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard capturing, bounds.contains(point), let session else { return false }
        guard let touches = event?.allTouches, !touches.isEmpty else { return true }
        let state = session.current
        return touches.contains { DrawCommands.shared.capturesPointer(state: state, pointer: DrawingTouches.pointer(of: $0)) }
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer.view !== self, capturing else {
            return super.gestureRecognizerShouldBegin(gestureRecognizer)
        }
        return false
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let session, !navigating else { return }
        if strokeTouch != nil || activeTouches(event).count > 1 {
            beginNavigation()
            return
        }
        guard let touch = touches.first else { return }
        let commands = DrawCommands.shared
        let pointer = DrawingTouches.pointer(of: touch)
        if commands.capturesPointer(state: session.current, pointer: pointer) {
            strokeTouch = touch
            session.dispatch(commands.pointerDown(point: sample(touch), pointer: pointer, anchors: currentAnchors()))
        } else if navigator != nil {
            navigating = true
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if navigating {
            navigate(event)
            return
        }
        guard let session, let stroke = strokeTouch, touches.contains(stroke) else { return }
        let samples = (event?.coalescedTouches(for: stroke) ?? [stroke]).map { sample($0) }
        session.dispatch(DrawCommands.shared.pointerMove(points: samples))
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        release(touches, event: event, cancelled: false)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        release(touches, event: event, cancelled: true)
    }

    private var capturing: Bool {
        guard inputEnabled, let session else { return false }
        return DrawCommands.shared.capturesTouches(state: session.current)
    }

    private func stateChanged(_ state: DrawEditorState) {
        let view = DrawCommands.shared.paperView(state: state)
        if paper != view {
            paper = view
            setNeedsDisplay()
        }
        ink.setNeedsDisplay()
    }

    private func currentAnchors() -> [DrawAnchorFrame] {
        anchors?.frames(in: bounds.size) ?? []
    }

    private func updateResolution() {
        let screen = window?.screen.scale ?? UIScreen.main.scale
        let longest = max(bounds.width, bounds.height, 1)
        let resolved = max(min(screen * max(zoom, 1), Self.maxPixels / longest), 1)
        guard contentScaleFactor != resolved || ink.contentScaleFactor != resolved else { return }
        contentScaleFactor = resolved
        ink.contentScaleFactor = resolved
        setNeedsDisplay()
        ink.setNeedsDisplay()
    }

    private func relaxScrolls() {
        let wanted = inputEnabled && window != nil
        if wanted && relaxed.isEmpty {
            var view = superview
            while let current = view {
                if let scroll = current as? UIScrollView, scroll.delaysContentTouches {
                    relaxed.append(DrawingRelaxedScroll(scroll))
                }
                view = current.superview
            }
        } else if !wanted {
            relaxed.forEach { $0.restore() }
            relaxed.removeAll()
        }
    }

    private func sample(_ touch: UITouch) -> DrawPoint {
        let location = touch.preciseLocation(in: self)
        let pencil = touch.type == .pencil
        let pressure = touch.maximumPossibleForce > 0 ? touch.force / touch.maximumPossibleForce : 1
        let tilt = pencil ? CGFloat.pi / 2 - touch.altitudeAngle : 0
        let azimuth = pencil ? DrawingTouches.orientation(touch.azimuthAngle(in: self)) : 0
        return DrawCommands.shared.sample(
            x: Float(location.x),
            y: Float(location.y),
            pressure: Float(pressure),
            tilt: Float(tilt),
            azimuth: Float(azimuth),
            time: Int64(touch.timestamp * 1000)
        )
    }

    private func activeTouches(_ event: UIEvent?) -> Set<UITouch> {
        (event?.touches(for: self) ?? []).filter { $0.phase != .ended && $0.phase != .cancelled }
    }

    private func beginNavigation() {
        if strokeTouch != nil {
            strokeTouch = nil
            session?.dispatch(DrawCommands.shared.pointerCancel())
        }
        navigating = true
    }

    private func navigate(_ event: UIEvent?) {
        guard let navigator else { return }
        let fingers = Array(activeTouches(event).prefix(2))
        guard !fingers.isEmpty else { return }
        let before = fingers.map { $0.previousLocation(in: self) }
        let after = fingers.map { $0.location(in: self) }
        let focus = DrawingTouches.centroid(before)
        let center = DrawingTouches.centroid(after)
        var scale: CGFloat = 1
        var rotation: CGFloat = 0
        if fingers.count > 1 {
            let span = DrawingTouches.distance(before[0], before[1])
            if span > 0 { scale = DrawingTouches.distance(after[0], after[1]) / span }
            rotation = DrawingTouches.turn(
                from: DrawingTouches.angle(before[0], before[1]),
                to: DrawingTouches.angle(after[0], after[1])
            )
        }
        navigator.navigate(
            panX: Float(center.x - focus.x),
            panY: Float(center.y - focus.y),
            zoom: Float(scale),
            rotation: Float(rotation),
            focusX: Float(focus.x),
            focusY: Float(focus.y)
        )
    }

    private func release(_ touches: Set<UITouch>, event: UIEvent?, cancelled: Bool) {
        if let stroke = strokeTouch, touches.contains(stroke) {
            strokeTouch = nil
            let commands = DrawCommands.shared
            session?.dispatch(cancelled ? commands.pointerCancel() : commands.pointerUp())
        }
        guard navigating, activeTouches(event).subtracting(touches).isEmpty else { return }
        navigating = false
        navigator?.end()
    }

    private func cancelGesture() {
        if strokeTouch != nil {
            strokeTouch = nil
            session?.dispatch(DrawCommands.shared.pointerCancel())
        }
        if navigating {
            navigating = false
            navigator?.end()
        }
    }
}

final class DrawingInkView: UIView {

    weak var owner: DrawingCanvasView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func draw(_ rect: CGRect) {
        owner?.drawInk(in: bounds)
    }
}

private final class DrawingRelaxedScroll {

    private weak var scroll: UIScrollView?

    init(_ scroll: UIScrollView) {
        self.scroll = scroll
        scroll.delaysContentTouches = false
    }

    func restore() {
        scroll?.delaysContentTouches = true
    }
}

enum DrawingTouches {

    static func pointer(of touch: UITouch) -> DrawPointerType {
        switch touch.type {
        case .pencil: return DrawCommands.shared.stylus()
        case .indirectPointer: return DrawCommands.shared.mouse()
        default: return DrawCommands.shared.finger()
        }
    }

    static func orientation(_ azimuth: CGFloat) -> CGFloat {
        turn(from: 0, to: azimuth + .pi / 2)
    }

    static func centroid(_ points: [CGPoint]) -> CGPoint {
        guard !points.isEmpty else { return .zero }
        let sum = points.reduce(CGPoint.zero) { CGPoint(x: $0.x + $1.x, y: $0.y + $1.y) }
        return CGPoint(x: sum.x / CGFloat(points.count), y: sum.y / CGFloat(points.count))
    }

    static func distance(_ first: CGPoint, _ second: CGPoint) -> CGFloat {
        hypot(second.x - first.x, second.y - first.y)
    }

    static func angle(_ first: CGPoint, _ second: CGPoint) -> CGFloat {
        atan2(second.y - first.y, second.x - first.x)
    }

    static func turn(from start: CGFloat, to end: CGFloat) -> CGFloat {
        var delta = end - start
        while delta > .pi { delta -= 2 * .pi }
        while delta < -.pi { delta += 2 * .pi }
        return delta
    }
}
