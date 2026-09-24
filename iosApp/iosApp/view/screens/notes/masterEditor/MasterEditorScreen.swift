import SwiftUI
import shared

struct MasterEditorScreen: View {

    let noteId: String?
    var onOpenMedia: (String?) -> Void

    // seeded once with the id, like NoteEditorView: the view model does the load in init, so
    // a nil id creates exactly one new note instead of one per appearance
    @StateObject private var viewModel: MasterEditorViewModel

    init(noteId: String?, onOpenMedia: @escaping (String?) -> Void = { _ in }) {
        self.noteId = noteId
        self.onOpenMedia = onOpenMedia
        _viewModel = StateObject(wrappedValue: MasterEditorViewModel(noteId: noteId))
    }
    @Environment(Router.self) private var router: Router
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    @State private var showAttach = false
    @State private var sheet: MasterTextSheet = .none
    @State private var keyboardHeight: CGFloat = 0
    @State private var autoFocused = false

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    private var commands: CanvasCommands { CanvasCommands.shared }

    var body: some View {
        ZStack(alignment: .bottom) {
            canvas
            banner
            zoomBar
            keyboardToolbar
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.page.ignoresSafeArea())
        .onAppear { viewModel.refresh() }
        .onReceive(
            NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)
        ) { note in
            guard let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect
            else { return }
            keyboardHeight = max(UIScreen.main.bounds.height - frame.origin.y, 0)
        }
        .onReceive(
            NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)
        ) { _ in keyboardHeight = 0 }
        .confirmationDialog(
            "Add beside the focused widget",
            isPresented: $showAttach,
            titleVisibility: .visible
        ) {
            attachActions
        }
        .editorCapabilities(
            state: viewModel.capabilities,
            noteTitle: viewModel.note?.title ?? "this note",
            callbacks: EditorCapabilityCallbacks(
                onState: { viewModel.onCapabilityState($0) },
                onOpenCamera: {
                    router.navigate(to: .Camera { media in viewModel.onCaptured(media) })
                },
                onCaptured: { viewModel.onCaptured($0) },
                onFilesPicked: { viewModel.importFiles(urls: $0) },
                onImportLink: { viewModel.importFromLink($0) },
                onDeleteContent: { viewModel.deleteContent($0) },
                onDeleteNote: { dismiss() },
                onPermissionDenied: { viewModel.onPermissionDenied($0) }
            )
        )
        .sheet(isPresented: Binding(
            get: { sheet.isPresented },
            set: { if !$0 { sheet = .none } }
        )) {
            if let editingId = viewModel.canvas.editingNodeId,
               let focused = viewModel.text(for: editingId) {
                MasterTextSheetHost(
                    sheet: sheet,
                    toolbar: focused.toolbar,
                    onIntent: { viewModel.onTextIntent(nodeId: editingId, intent: $0) },
                    onDismiss: { sheet = .none }
                )
            }
        }
    }

    private var canvas: some View {
        MasterCanvas(
            state: viewModel.canvas,
            onIntent: viewModel.onCanvasIntent,
            onRename: { nodeId, name in viewModel.renameNode(nodeId: nodeId, name: name) },
            onMeasured: { nodeId, height in
                viewModel.onWidgetMeasured(nodeId: nodeId, height: height)
            },
            keyboardInset: keyboardHeight,
            coverTitle: viewModel.note?.title
        ) { node, isEditing in
            nodeBody(node: node, isEditing: isEditing)
        }
        // without this SwiftUI lifts the whole canvas when the keyboard opens, which moves
        // the page off the screen it was fitted to
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .onAppear { autoFocusLastPage() }
        .onChange(of: autoFocusKey) { _, _ in autoFocusLastPage() }
    }

    private var autoFocusKey: String {
        let target = commands.lastPageId(state: viewModel.canvas) ?? ""
        return "\(target)@\(Int(viewModel.canvas.viewport.widthPx))"
    }

    /// 📄 A note opens on its LAST page, fitted to the screen and scrolled to its end — the same
    /// as tapping that page. The keyboard stays down until a text widget is tapped.
    private func autoFocusLastPage() {
        guard !autoFocused,
              viewModel.canvas.viewport.widthPx > 0,
              viewModel.canvas.editingNodeId == nil,
              let target = commands.lastPageId(state: viewModel.canvas) else { return }
        autoFocused = true
        viewModel.onCanvasIntent(commands.focusPage(pageId: target))
    }

    private func nodeBody(node: CanvasNode, isEditing: Bool) -> MasterNodeContent {
        let nodeId: String = node.id
        let scale = CGFloat(viewModel.canvas.viewport.scale)
        return MasterNodeContent(
            node: node,
            isEditing: isEditing,
            scale: scale,
            textState: viewModel.text(for: nodeId),
            content: viewModel.content(for: node),
            dismissToken: viewModel.keyboardDismissToken,
            onTextIntent: { intent in viewModel.onTextIntent(nodeId: nodeId, intent: intent) },
            onFocused: { viewModel.onCanvasIntent(commands.setEditing(nodeId: nodeId)) },
            onOpenMedia: { onOpenMedia(node.contentId) },
            onDelete: { viewModel.deleteNode(nodeId: nodeId) },
            keyboardInsetPx: keyboardHeight
        )
    }

    /// Shown only while the canvas has nothing to draw. Tells us which stage of
    /// load -> read note -> read canvas -> measure viewport actually failed.
    @ViewBuilder
    private var banner: some View {
        if viewModel.canvas.document.nodes.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("No canvas nodes yet")
                    .font(.system(size: 14, weight: .semibold))
                Text("note id: \(viewModel.note?.id ?? "nil")")
                Text("contents: \(viewModel.noteContents.count)  ·  texts: \(viewModel.texts.count)")
                Text("viewport: \(Int(viewModel.canvas.viewport.widthPx))×\(Int(viewModel.canvas.viewport.heightPx)) @ \(viewModel.canvas.zoomPercent)%")
                Text("loading: \(viewModel.isLoading ? "yes" : "no")")
                if let message = viewModel.errorMessage {
                    Text(message).foregroundColor(palette.accent)
                }
            }
            .font(.system(size: 12, design: .monospaced))
            .foregroundColor(palette.onSurface)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.codeBackground)
            .frame(maxHeight: .infinity, alignment: .top)
        }
    }

    @ViewBuilder
    private var keyboardToolbar: some View {
        if let editingId = viewModel.canvas.editingNodeId,
           let focused = viewModel.text(for: editingId) {
            SmartTextKeyboardToolbar(
                toolbar: focused.toolbar,
                expanded: focused.isToolbarExpanded,
                canUndo: focused.canUndo,
                canRedo: focused.canRedo,
                onAction: { action in handle(action, nodeId: editingId, state: focused) }
            )
        }
    }

    @ViewBuilder
    private var attachActions: some View {
        Button("New page") { viewModel.addPage() }
        Button("Text block") { viewModel.addWidget(kind: ContentType.text) }
        Button("Photo or video") { viewModel.requestCapture(ContentType.image) }
        Button("Record audio") { viewModel.requestCapture(ContentType.audio) }
        Button("Location") { viewModel.requestCapture(ContentType.location) }
        Button("Import a file") { viewModel.openImportSheet() }
        Button("Table") { viewModel.addWidget(kind: ContentType.table) }
        Button("Drawing") { viewModel.addWidget(kind: ContentType.drawing) }
        Button("Rebuild layout from note order") { viewModel.rebuildLayoutFromNote() }
        Button("Apply canvas order back to the note") { viewModel.applyCanvasOrderToNote() }
        Button("Cancel", role: .cancel) {}
    }

    private var zoomLabel: String { "\(viewModel.canvas.zoomPercent)%" }

    private var zoomBar: some View {
        HStack(spacing: 4) {
            barButton(icon: "minus", tint: palette.onSurface) {
                viewModel.onCanvasIntent(commands.zoomOut())
            }
            Text(zoomLabel)
                .font(.system(size: 13))
                .foregroundColor(palette.onSurfaceMuted)
            barButton(icon: "plus", tint: palette.onSurface) {
                viewModel.onCanvasIntent(commands.zoomIn())
            }
            barButton(icon: "viewfinder", tint: palette.onSurface) {
                viewModel.onCanvasIntent(commands.zoomToFit())
            }
            toolButtons
            barButton(icon: "plus.square.on.square", tint: palette.accent) {
                showAttach = true
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 12).fill(palette.toolbar))
        .padding(.bottom, 28)
    }

    private var toolButtons: some View {
        ForEach(commands.tools(), id: \.self) { tool in
            let active = commands.isTool(state: viewModel.canvas, tool: tool)
            barButton(icon: icon(for: tool), tint: active ? palette.accent : palette.onSurface) {
                viewModel.onCanvasIntent(commands.setTool(tool: tool))
            }
        }
    }

    private func icon(for tool: CanvasTool) -> String {
        switch tool {
        case CanvasTool.hand: return "hand.raised.fill"
        case CanvasTool.zoom: return "plus.magnifyingglass"
        case CanvasTool.lock: return "lock.fill"
        default: return "hand.point.up.left"
        }
    }

    private func barButton(
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon).foregroundColor(tint)
        }
    }

    private func handle(_ action: ToolbarAction, nodeId: String, state: MasterTextState) {
        let master = MasterTextCommands.shared
        if SmartTextCommands.shared.isMore(action: action) {
            viewModel.onTextIntent(
                nodeId: nodeId,
                intent: master.setToolbarExpanded(expanded: !state.isToolbarExpanded)
            )
        } else if SmartTextCommands.shared.isDismiss(action: action) {
            viewModel.keyboardDismissToken &+= 1
            viewModel.onCanvasIntent(commands.setEditing(nodeId: nil))
        } else if let intent = master.forToolbar(action: action) {
            viewModel.onTextIntent(nodeId: nodeId, intent: intent)
        } else {
            // style / colour / size / align / link have no intent — they open a sheet
            let opened = MasterTextSheet.of(action: action)
            if opened.isPresented {
                viewModel.keyboardDismissToken &+= 1
                sheet = opened
            }
        }
    }
}

struct MasterNodeContent: View {

    let node: CanvasNode
    let isEditing: Bool
    let scale: CGFloat
    let textState: MasterTextState?
    let content: NoteContentModel?
    let dismissToken: Int
    let onTextIntent: (MasterTextIntent) -> Void
    let onFocused: () -> Void
    var onOpenMedia: () -> Void = {}
    var onDelete: () -> Void = {}
    var keyboardInsetPx: CGFloat = 0

    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    private var media: NoteContentModel.MediaContent? {
        content as? NoteContentModel.MediaContent
    }

    @ViewBuilder
    var body: some View {
        // 📄 paper owns no content of its own — the widgets dropped on it draw themselves
        if node.isPage {
            Color.clear
        } else if content is NoteContentModel.TextContent {
            textBody
        } else if let media {
            switch media.type {
            case ContentType.image, ContentType.gif, ContentType.video, ContentType.audio:
                mediaBody

            case ContentType.docx, ContentType.epub, ContentType.md, ContentType.txt,
                 ContentType.pdf, ContentType.other:
                // 🧱 24-Sep-2026 — the compact card the canvas gave it, and no margin of its own
                InlineBookFileView(
                    media: media,
                    onOpenFull: onOpenMedia,
                    onDelete: onDelete,
                    cardHeight: cardSize.height
                )

            default:
                placeholder("Media")
            }
        } else if content is NoteContentModel.Link {
            linkBody
        } else if content is NoteContentModel.Location {
            locationBody
        } else if node.contentId != nil {
            // 🔄 24-Sep-2026 — a widget waiting for its content (it may still be on its way from the other device) draws nothing
            Color.clear
        } else {
            placeholder(node.kind.name)
        }
    }

    // 🧱 24-Sep-2026 — a card is laid out at its own canvas size; the canvas zooms it like a picture
    private var cardSize: CGSize {
        CGSize(width: CGFloat(node.rect.width), height: CGFloat(node.rect.height))
    }

    @ViewBuilder
    private var textBody: some View {
        if let textState {
            // NOT readOnly: !isEditing — flipping editability as the view becomes first
            // responder tears down and rebuilds the input session, which closed the
            // keyboard and reopened it. Focus drives editingNodeId, never the reverse.
            MasterTextWidget(
                state: textState,
                readOnly: false,
                scale: scale,
                accessory: nil,
                dismissToken: dismissToken,
                shouldFocus: isEditing,
                scrollable: false,
                minLines: 5,
                keyboardInsetPx: isEditing ? keyboardInsetPx : 0,
                reserveKeyboardRoom: false,
                onFocused: onFocused,
                onIntent: onTextIntent
            )
            // 🧱 24-Sep-2026 — no margin of its own: the page's 8pt spacing is the only space around a widget
            .frame(maxHeight: .infinity, alignment: .top)
        } else {
            placeholder("Empty text")
        }
    }

    @ViewBuilder
    private var mediaBody: some View {
        if let media {
            switch media.type {
            case ContentType.image, ContentType.gif:
                CardImageEditor(
                    content: media,
                    actionClick: onOpenMedia,
                    actionDelete: onDelete,
                    cardWidth: cardSize.width,
                    cardHeight: cardSize.height
                )

            case ContentType.video:
                VideoCardPlayer(
                    content: media,
                    cardWidth: cardSize.width,
                    cardHeight: cardSize.height,
                    cardPadding: 0,
                    actionClick: onOpenMedia,
                    actionDelete: onDelete
                )

            case ContentType.audio:
                AudioPlayView(mediaContent: media, onDelete: onDelete)

            default:
                placeholder("Media")
            }
        } else {
            placeholder("Media")
        }
    }

    private var linkBody: some View {
        let link = content as? NoteContentModel.Link
        return VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "link").foregroundColor(palette.accent)
            Text(link?.url.isEmpty == false ? link!.url : "Link")
                .font(.system(size: 15))
                .foregroundColor(palette.accent)
                .lineLimit(3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(16)
        .contentShape(Rectangle())
        .onTapGesture {
            if let raw = link?.url, let url = URL(string: raw) { openURL(url) }
        }
    }

    private var locationBody: some View {
        let location = content as? NoteContentModel.Location
        let label: String = {
            if let address = location?.address, !address.isEmpty { return address }
            if let location { return "\(location.latitude), \(location.longitude)" }
            return "Location"
        }()
        return VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "mappin.and.ellipse").foregroundColor(palette.accent)
            Text(label)
                .font(.system(size: 15))
                .foregroundColor(palette.onSurface)
                .lineLimit(3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(16)
        .contentShape(Rectangle())
        .onTapGesture {
            guard let location,
                  let url = URL(string: "maps://?ll=\(location.latitude),\(location.longitude)")
            else { return }
            openURL(url)
        }
    }

    private func placeholder(_ label: String) -> some View {
        ZStack {
            palette.codeBackground
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(palette.onSurfaceMuted)
        }
    }
}
