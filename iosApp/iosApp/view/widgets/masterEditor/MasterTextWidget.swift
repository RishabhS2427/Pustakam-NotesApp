import SwiftUI
import UIKit
import shared

struct MasterTextWidget: UIViewRepresentable {

    static let lineHeight: CGFloat = 1.2

    let state: MasterTextState
    var readOnly: Bool = false
    var scale: CGFloat = 1
    var accessory: AnyView?
    var dismissToken: Int = 0
    var shouldFocus: Bool = false
    var scrollable: Bool = true
    var minLines: Int = 1
    var placeholder: String = "Keep your thoughts alive."
    var keyboardInsetPx: CGFloat = 0
    // 📄 a canvas page makes the keyboard room on its own content; with several text views on
    //   one page, each padding itself by the keyboard pushed everything under it down
    var reserveKeyboardRoom: Bool = true
    var onFocused: () -> Void = {}
    let onIntent: (MasterTextIntent) -> Void

    @Environment(\.colorScheme) private var scheme

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    private var baseSize: CGFloat {
        SmartTextMetrics.baseFontSize * scale * CGFloat(CanvasNode.companion.BASE_FONT_SCALE)
    }

    func makeUIView(context: Context) -> MasterTextUITextView {
        let textView = MasterTextUITextView()
        textView.delegate = context.coordinator
        textView.backgroundColor = .clear
        // scrolls inside the fixed node frame — mirrors Android's verticalScroll(). With this off,
        // sizeThatFits laid out the whole document on every SwiftUI pass and froze the canvas.
        textView.isScrollEnabled = scrollable
        textView.isEditable = !readOnly
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.keyboardDismissMode = .interactive
        textView.tintColor = UIColor(palette.accent)
        textView.adjustsFontForContentSizeCategory = true
        textView.dataDetectorTypes = []
        textView.alwaysBounceVertical = false
        textView.showsVerticalScrollIndicator = false
        textView.setContentCompressionResistancePriority(.required, for: .vertical)
        textView.setContentHuggingPriority(.required, for: .vertical)
        textView.configure(palette: palette, baseSize: baseSize)

        // both must let touches through, or UITextView never gets the tap that places the
        // caret and makes it first responder — the field looks alive but cannot be edited
        let doubleTap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleDoubleTap(_:))
        )
        doubleTap.numberOfTapsRequired = 2
        doubleTap.cancelsTouchesInView = false
        doubleTap.delaysTouchesEnded = false
        doubleTap.delegate = context.coordinator
        textView.addGestureRecognizer(doubleTap)

        let singleTap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleSingleTap(_:))
        )
        singleTap.cancelsTouchesInView = false
        singleTap.delaysTouchesEnded = false
        singleTap.delegate = context.coordinator
        textView.addGestureRecognizer(singleTap)

        return textView
    }

    func updateUIView(_ uiView: MasterTextUITextView, context: Context) {
        context.coordinator.parent = self
        uiView.isEditable = !readOnly
        uiView.isScrollEnabled = scrollable
        uiView.configure(palette: palette, baseSize: baseSize)
        // room to scroll the end of the text clear of the keyboard, and the caret clear of that
        uiView.textContainerInset.bottom = !reserveKeyboardRoom || keyboardInsetPx <= 0 ? 0 : keyboardInsetPx + CGFloat(
            CanvasCommands.shared.caretRevealPadding(
                lineHeightPx: Float(baseSize * MasterTextWidget.lineHeight)
            )
        )
        uiView.masterState = state

        let rendered = MasterTextRenderer.attributed(
            state: state,
            palette: palette,
            baseSize: baseSize
        )
        if uiView.attributedText != rendered {
            let previous = uiView.selectedRange
            // assigning attributedText fires textViewDidChangeSelection synchronously; without
            // this flag that delegate publishes new state from inside the SwiftUI update pass
            context.coordinator.isSyncing = true
            uiView.attributedText = rendered
            let limit = (state.text as NSString).length
            uiView.selectedRange = NSRange(
                location: min(previous.location, limit),
                length: min(previous.length, max(limit - min(previous.location, limit), 0))
            )
            context.coordinator.isSyncing = false
            uiView.setNeedsDisplay()
        }
        uiView.showPlaceholder(state.text.isEmpty ? placeholder : nil)
        context.coordinator.syncAccessory(on: uiView, accessory: accessory)
        context.coordinator.syncFirstResponder(uiView)
    }

    /// Canvas pages take the frame they are given and scroll inside it. In the note editor the
    /// block has to grow with its text instead, so there we measure.
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: MasterTextUITextView, context: Context) -> CGSize? {
        let width = proposal.width ?? UIScreen.main.bounds.width
        if scrollable {
            return CGSize(width: width, height: proposal.height ?? baseSize * 2)
        }
        let fitted = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        let floorHeight = baseSize * MasterTextWidget.lineHeight * CGFloat(minLines)
        return CGSize(width: width, height: max(fitted.height, floorHeight))
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UITextViewDelegate, UIGestureRecognizerDelegate {

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool { true }

        var parent: MasterTextWidget
        private var accessoryHost: UIHostingController<AnyView>?
        private var accessoryContainer: SmartTextAccessoryView?
        private var lastDismissToken: Int = 0
        private var claimedFocus = false
        var isSyncing = false

        init(_ parent: MasterTextWidget) {
            self.parent = parent
        }

        /// UIKit's counterpart to Compose's onFocusChanged — the node that gains the caret
        /// becomes the editing node, with the caret parked at the end of the text.
        func textViewDidBeginEditing(_ textView: UITextView) {
            guard !isSyncing else { return }
            let end = (textView.text as NSString?)?.length ?? 0
            textView.selectedRange = NSRange(location: end, length: 0)
            parent.onFocused()
            DispatchQueue.main.async { [weak self, weak textView] in
                guard let textView else { return }
                self?.revealCaret(textView)
            }
        }

        func textViewDidChange(_ textView: UITextView) {
            guard !isSyncing else { return }
            revealCaret(textView)
            let range = textView.selectedRange
            parent.onIntent(
                MasterTextCommands.shared.edit(
                    text: textView.text ?? "",
                    selectionStart: Int32(range.location),
                    selectionEnd: Int32(range.location + range.length)
                )
            )
        }

        /// Scrolls the caret clear of the keyboard inside whichever scroll view owns the text —
        /// the page body on the canvas. The canvas viewport is never touched, so the page
        /// stays locked where it was fitted.
        func revealCaret(_ textView: UITextView) {
            guard let range = textView.selectedTextRange else { return }
            let caret = textView.caretRect(for: range.end)
            guard caret.height.isFinite, !caret.isNull else { return }
            guard let scroll = scrollHost(of: textView) else { return }

            let rect = textView.convert(caret, to: scroll)
            let clearance = CGFloat(
                CanvasCommands.shared.caretRevealPadding(lineHeightPx: Float(caret.height))
            )
            let top = scroll.contentOffset.y
            let bottom = top + scroll.bounds.height - parent.keyboardInsetPx
            var target = top
            if rect.maxY + clearance > bottom {
                target = top + (rect.maxY + clearance - bottom)
            } else if rect.minY < top {
                target = rect.minY
            } else {
                return
            }
            let limit = max(scroll.contentSize.height - scroll.bounds.height, 0)
            let settled = min(max(target, 0), limit)
            guard abs(settled - scroll.contentOffset.y) > 0.5 else { return }
            scroll.setContentOffset(CGPoint(x: scroll.contentOffset.x, y: settled), animated: true)
        }

        /// The text view when it scrolls itself, otherwise the nearest scrolling ancestor.
        private func scrollHost(of textView: UITextView) -> UIScrollView? {
            if textView.isScrollEnabled { return textView }
            var next = textView.superview
            while let candidate = next {
                if let scroll = candidate as? UIScrollView { return scroll }
                next = candidate.superview
            }
            return nil
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard !isSyncing else { return }
            revealCaret(textView)
            guard textView.text == parent.state.text else { return }
            let range = textView.selectedRange
            parent.onIntent(
                MasterTextCommands.shared.selectionChanged(
                    start: Int32(range.location),
                    end: Int32(range.location + range.length)
                )
            )
        }

        func textView(
            _ textView: UITextView,
            shouldInteractWith URL: URL,
            in characterRange: NSRange,
            interaction: UITextItemInteraction
        ) -> Bool {
            UIApplication.shared.open(URL)
            return false
        }

        @objc func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
            guard let textView = recognizer.view as? UITextView else { return }
            let point = recognizer.location(in: textView)
            let offset = textView.closestPosition(to: point).map {
                textView.offset(from: textView.beginningOfDocument, to: $0)
            } ?? 0
            parent.onIntent(MasterTextCommands.shared.selectWord(offset: Int32(offset)))
        }

        @objc func handleSingleTap(_ recognizer: UITapGestureRecognizer) {
            guard let textView = recognizer.view as? MasterTextUITextView else { return }
            // read-only means the node is not the editing node yet — a tap claims it,
            // the same way Android's onFocusChanged does
            guard !parent.readOnly else {
                parent.onFocused()
                return
            }
            let point = recognizer.location(in: textView)
            guard let offset = MasterTextRenderer.checklistOffset(
                state: parent.state,
                textView: textView,
                point: point,
                baseSize: parent.baseSize
            ) else { return }
            parent.onIntent(MasterTextCommands.shared.toggleChecked(offset: Int32(offset)))
        }

        func syncFirstResponder(_ textView: UITextView) {
            guard !parent.readOnly else { return }
            if lastDismissToken != parent.dismissToken {
                lastDismissToken = parent.dismissToken
                claimedFocus = true
                if textView.isFirstResponder { textView.resignFirstResponder() }
                return
            }
            guard parent.shouldFocus else {
                claimedFocus = false
                return
            }
            if !textView.isFirstResponder, !claimedFocus {
                claimedFocus = true
                DispatchQueue.main.async { textView.becomeFirstResponder() }
            }
        }

        func syncAccessory(on textView: UITextView, accessory: AnyView?) {
            guard let accessory else {
                textView.inputAccessoryView = nil
                accessoryHost = nil
                accessoryContainer = nil
                return
            }
            let width = UIScreen.main.bounds.width
            let host: UIHostingController<AnyView>
            let container: SmartTextAccessoryView
            if let existingHost = accessoryHost, let existingContainer = accessoryContainer {
                host = existingHost
                container = existingContainer
                host.rootView = accessory
            } else {
                host = UIHostingController(rootView: accessory)
                host.view.backgroundColor = .clear
                host.view.translatesAutoresizingMaskIntoConstraints = false
                if #available(iOS 16.4, *) { host.safeAreaRegions = [] }
                host.view.insetsLayoutMarginsFromSafeArea = false
                container = SmartTextAccessoryView()
                container.addSubview(host.view)
                NSLayoutConstraint.activate([
                    host.view.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                    host.view.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                    host.view.topAnchor.constraint(equalTo: container.topAnchor),
                    host.view.bottomAnchor.constraint(equalTo: container.bottomAnchor)
                ])
                accessoryHost = host
                accessoryContainer = container
            }
            let fitted = host.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude))
            let grew = container.apply(height: fitted.height)
            if textView.inputAccessoryView !== container {
                textView.inputAccessoryView = container
                if textView.isFirstResponder { textView.reloadInputViews() }
            } else if grew, textView.isFirstResponder {
                textView.reloadInputViews()
            }
        }
    }
}

final class MasterTextUITextView: UITextView {

    var masterState: MasterTextState?
    private var palette: SmartTextPalette = .light
    private var baseSize: CGFloat = 16
    private weak var placeholderLabel: UILabel?

    func configure(palette: SmartTextPalette, baseSize: CGFloat) {
        self.palette = palette
        self.baseSize = baseSize
    }

    func showPlaceholder(_ text: String?) {
        guard let text else {
            placeholderLabel?.removeFromSuperview()
            placeholderLabel = nil
            return
        }
        if let label = placeholderLabel {
            label.text = text
            label.textColor = UIColor(palette.onSurfaceMuted)
            label.font = .systemFont(ofSize: baseSize)
            return
        }
        let label = UILabel()
        label.text = text
        label.textColor = UIColor(palette.onSurfaceMuted)
        label.font = .systemFont(ofSize: baseSize)
        label.numberOfLines = 1
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: leadingAnchor),
            label.topAnchor.constraint(equalTo: topAnchor, constant: 2)
        ])
        placeholderLabel = label
    }

    override func draw(_ rect: CGRect) {
        super.draw(rect)
        guard let state = masterState else { return }
        for entry in MasterTextRenderer.markerFrames(
            state: state,
            textView: self,
            baseSize: baseSize
        ) {
            if entry.paragraph.isChecklist {
                drawCheckbox(at: entry.origin, checked: entry.paragraph.checked)
            } else {
                drawGlyph(entry.paragraph.marker, at: entry.origin, size: entry.size)
            }
        }
    }

    private func drawGlyph(_ glyph: String, at point: CGPoint, size: CGFloat) {
        (glyph as NSString).draw(
            at: point,
            withAttributes: [
                .font: UIFont.systemFont(ofSize: size, weight: .medium),
                .foregroundColor: UIColor(palette.accent)
            ]
        )
    }

    private func drawCheckbox(at point: CGPoint, checked: Bool) {
        let side: CGFloat = MasterTextRenderer.checkboxSide
        let stroke: CGFloat = 1.5
        let box = CGRect(x: point.x, y: point.y, width: side, height: side)

        guard checked else {
            let ring = UIBezierPath(ovalIn: box.insetBy(dx: stroke / 2, dy: stroke / 2))
            UIColor(palette.onSurfaceMuted).setStroke()
            ring.lineWidth = stroke
            ring.stroke()
            return
        }

        UIColor(palette.accent).setFill()
        UIBezierPath(ovalIn: box).fill()

        let tick = UIBezierPath()
        tick.move(to: CGPoint(x: box.minX + side * 0.28, y: box.minY + side * 0.52))
        tick.addLine(to: CGPoint(x: box.minX + side * 0.44, y: box.minY + side * 0.68))
        tick.addLine(to: CGPoint(x: box.minX + side * 0.74, y: box.minY + side * 0.34))
        tick.lineWidth = stroke
        tick.lineCapStyle = .round
        tick.lineJoinStyle = .round
        UIColor(palette.surface).setStroke()
        tick.stroke()
    }
}

struct MasterTextContentWidget: View {

    let text: String
    let metadata: RichTextMetadata?
    var readOnly: Bool = false
    var showKeyboardToolbar: Bool = true
    let onDocumentChange: (RichDocument) -> Void

    @State private var state: MasterTextState
    @State private var lastEmitted: RichDocument
    @State private var sheet: MasterTextSheet = .none
    @State private var dismissToken = 0

    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL

    init(
        text: String,
        metadata: RichTextMetadata?,
        readOnly: Bool = false,
        showKeyboardToolbar: Bool = true,
        onDocumentChange: @escaping (RichDocument) -> Void
    ) {
        self.text = text
        self.metadata = metadata
        self.readOnly = readOnly
        self.showKeyboardToolbar = showKeyboardToolbar
        self.onDocumentChange = onDocumentChange
        let document = RichTextCodec.shared.documentFrom(text: text, metadata: metadata)
        _state = State(initialValue: MasterTextState.companion.of(document: document))
        _lastEmitted = State(initialValue: document)
    }

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    var body: some View {
        MasterTextWidget(
            state: state,
            readOnly: readOnly,
            accessory: keyboardAccessory,
            dismissToken: dismissToken,
            scrollable: false,
            onIntent: dispatch
        )
        .sheet(isPresented: Binding(
            get: { sheet.isPresented },
            set: { if !$0 { sheet = .none } }
        )) {
            MasterTextSheetHost(
                sheet: sheet,
                toolbar: state.toolbar,
                onIntent: dispatch,
                onDismiss: { sheet = .none }
            )
        }
        // @State is seeded once in init, so without this the widget keeps rendering the
        // document it was created with even after the note is edited somewhere else
        .onAppear { reseedIfNeeded() }
        .onChange(of: text) { _, _ in reseedIfNeeded() }
    }

    private func reseedIfNeeded() {
        let document = RichTextCodec.shared.documentFrom(text: text, metadata: metadata)
        guard document != lastEmitted, document != state.document else { return }
        state = MasterTextState.companion.of(document: document)
        lastEmitted = document
    }

    private var keyboardAccessory: AnyView? {
        guard showKeyboardToolbar, !readOnly else { return nil }
        return AnyView(
            SmartTextKeyboardToolbar(
                toolbar: state.toolbar,
                expanded: state.isToolbarExpanded,
                canUndo: state.canUndo,
                canRedo: state.canRedo,
                onAction: handle
            )
            .environment(\.colorScheme, scheme)
        )
    }

    private func dispatch(_ intent: MasterTextIntent) {
        let next = MasterTextReducer.shared.reduce(state: state, intent: intent)
        state = next
        if next.document != lastEmitted {
            lastEmitted = next.document
            onDocumentChange(next.document)
        }
    }

    private func handle(_ action: ToolbarAction) {
        let commands = SmartTextCommands.shared
        if commands.isMore(action: action) {
            dispatch(
                MasterTextCommands.shared.setToolbarExpanded(expanded: !state.isToolbarExpanded)
            )
        } else if commands.isDismiss(action: action) {
            dismissToken &+= 1
        } else if let intent = MasterTextCommands.shared.forToolbar(action: action) {
            dispatch(intent)
        } else {
            let opened = MasterTextSheet.of(action: action)
            if opened.isPresented {
                dismissToken &+= 1
                sheet = opened
            }
        }
    }
}
