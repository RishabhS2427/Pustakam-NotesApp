import SwiftUI
import shared

struct DrawingPanelSheet: View {

    @ObservedObject var session: DrawingSession
    let panel: DrawPanel
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let state = session.state
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                content(state)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(SmartTextPalette.of(scheme).surface.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func content(_ state: DrawEditorState) -> some View {
        switch panel {
        case DrawPanel.brush: DrawingBrushPanel(session: session, state: state)
        case DrawPanel.eraser: DrawingEraserPanel(session: session, state: state)
        case DrawPanel.shape: DrawingShapePanel(session: session, state: state)
        case DrawPanel.color: DrawingColorPanel(session: session, state: state)
        case DrawPanel.paper: DrawingPaperPanel(session: session, state: state)
        case DrawPanel.settings: DrawingSettingsPanel(session: session, state: state, onDismiss: onDismiss)
        default: EmptyView()
        }
    }
}

private struct DrawingBrushPanel: View {

    let session: DrawingSession
    let state: DrawEditorState

    var body: some View {
        let commands = DrawCommands.shared
        DrawingSectionTitle(title: "Pen")
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(commands.brushKinds(), id: \.self) { kind in
                    DrawingChoiceCard(
                        selected: commands.isBrush(state: state, kind: kind),
                        label: commands.brushLabel(kind: kind),
                        onTap: { session.dispatch(commands.setBrushKind(kind: kind)) }
                    ) {
                        DrawingEntries { width, height in
                            commands.brushPreview(state: state, kind: kind, width: width, height: height)
                        }
                        .frame(width: 88, height: 40)
                    }
                }
            }
        }
        ForEach(commands.brushControls(state: state), id: \.self) { control in
            DrawingSlider(
                label: commands.brushControlLabel(control: control),
                text: commands.brushControlText(state: state, control: control),
                position: commands.brushControlPosition(state: state, control: control)
            ) { position in
                session.dispatch(commands.setBrushControl(state: session.current, control: control, position: position))
            }
        }
    }
}

private struct DrawingEraserPanel: View {

    let session: DrawingSession
    let state: DrawEditorState

    var body: some View {
        let commands = DrawCommands.shared
        DrawingSectionTitle(title: "Eraser")
        DrawingChips(
            items: commands.eraserKinds(),
            isSelected: { commands.isEraser(state: state, kind: $0) },
            label: { commands.eraserLabel(kind: $0) },
            onSelect: { session.dispatch(commands.setEraserKind(kind: $0)) }
        )
        DrawingSlider(
            label: "Size",
            text: commands.eraserText(state: state),
            position: commands.eraserPosition(state: state)
        ) { position in
            session.dispatch(commands.setEraserPosition(position: position))
        }
    }
}

private struct DrawingShapePanel: View {

    let session: DrawingSession
    let state: DrawEditorState

    var body: some View {
        let commands = DrawCommands.shared
        let stroke = commands.sizeControl()
        DrawingSectionTitle(title: "Shapes")
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(commands.shapeKinds(), id: \.self) { kind in
                    DrawingChoiceCard(
                        selected: commands.isShape(state: state, kind: kind),
                        label: commands.shapeLabel(kind: kind),
                        onTap: { session.dispatch(commands.setShapeKind(kind: kind)) }
                    ) {
                        DrawingEntries { width, height in
                            commands.shapePreview(state: state, kind: kind, width: width, height: height)
                        }
                        .frame(width: 44, height: 44)
                    }
                }
            }
        }
        ForEach(commands.shapeControls(state: state), id: \.self) { control in
            DrawingSlider(
                label: commands.shapeControlLabel(control: control),
                text: commands.shapeControlText(state: state, control: control),
                position: commands.shapeControlPosition(state: state, control: control)
            ) { position in
                session.dispatch(commands.setShapeControl(state: session.current, control: control, position: position))
            }
        }
        DrawingSlider(
            label: "Stroke",
            text: commands.brushControlText(state: state, control: stroke),
            position: commands.brushControlPosition(state: state, control: stroke)
        ) { position in
            session.dispatch(commands.setBrushControl(state: session.current, control: stroke, position: position))
        }
        if commands.canFillShape(state: state) {
            DrawingSwitchRow(label: "Fill", isOn: commands.isShapeFilled(state: state)) { filled in
                session.dispatch(commands.setShapeFilled(state: session.current, value: filled))
            }
        }
    }
}

private struct DrawingColorPanel: View {

    let session: DrawingSession
    let state: DrawEditorState

    @Environment(\.colorScheme) private var scheme
    @State private var pickerKey = 0

    var body: some View {
        let commands = DrawCommands.shared
        let palette = SmartTextPalette.of(scheme)
        let current = commands.currentColor(state: state)
        HStack {
            DrawingSectionTitle(title: "Colour", detail: commands.colorHex(color: current))
            Spacer()
            Button {
                session.dispatch(commands.toggleFavorite(color: current))
            } label: {
                Image(systemName: commands.isFavorite(state: state, color: current) ? DrawingIcons.favorite : DrawingIcons.notFavorite)
                    .foregroundColor(palette.accent)
            }
            .accessibilityLabel(Text("Favourite colour"))
        }
        ColorSelector(selectedColor: picked, showAlpha: true)
            .id(pickerKey)
        let recent = commands.recentColors(state: state)
        if !recent.isEmpty {
            DrawingSectionTitle(title: "Recent")
            DrawingSwatches(colors: recent, state: state, onTap: pick)
        }
        let favorites = commands.favoriteColors(state: state)
        if !favorites.isEmpty {
            DrawingSectionTitle(title: "Favourites")
            DrawingSwatches(colors: favorites, state: state, onTap: pick)
        }
        ForEach(commands.palettes(state: state), id: \.id) { drawPalette in
            HStack {
                DrawingSectionTitle(title: drawPalette.name)
                Spacer()
                if drawPalette.editable {
                    Button("Add colour") {
                        session.dispatch(commands.addToPalette(paletteId: drawPalette.id, color: current))
                    }
                    .foregroundColor(palette.accent)
                    Button("Delete", role: .destructive) {
                        session.dispatch(commands.removePalette(paletteId: drawPalette.id))
                    }
                }
            }
            DrawingSwatches(colors: drawPalette.colors, state: state, onTap: pick, onLongPress: removal(from: drawPalette))
        }
        Button("New palette") {
            session.dispatch(commands.addPalette(name: commands.nextPaletteName(state: state), colors: [current]))
        }
        .foregroundColor(palette.accent)
    }

    private var picked: Binding<Color> {
        Binding(
            get: { DrawCommands.shared.currentColor(state: state).swiftUI },
            set: { color in
                let commands = DrawCommands.shared
                let next = color.drawColor
                if !commands.isSameColor(first: next, second: commands.currentColor(state: session.current)) {
                    session.dispatch(commands.setColor(color: next))
                }
            }
        )
    }

    private func pick(_ color: DrawColor) {
        session.dispatch(DrawCommands.shared.setColor(color: color))
        pickerKey += 1
    }

    private func removal(from drawPalette: DrawPalette) -> ((DrawColor) -> Void)? {
        guard drawPalette.editable else { return nil }
        return { color in
            session.dispatch(DrawCommands.shared.removeFromPalette(paletteId: drawPalette.id, color: color))
        }
    }
}

private struct DrawingPaperPanel: View {

    let session: DrawingSession
    let state: DrawEditorState

    var body: some View {
        let commands = DrawCommands.shared
        let advanced = commands.isAdvanced(state: state)
        if commands.canResizePaper(state: state) {
            DrawingSectionTitle(title: "Size", detail: commands.paperSizeText(state: state))
            DrawingChips(
                items: commands.paperSizes(),
                isSelected: { commands.isPaperSize(state: state, size: $0) },
                label: { commands.paperLabel(size: $0) },
                onSelect: { session.dispatch(commands.setPaperSize(state: session.current, size: $0)) }
            )
            DrawingChips(
                items: commands.orientations(),
                isSelected: { commands.isOrientation(state: state, orientation: $0) },
                label: { commands.orientationLabel(orientation: $0) },
                onSelect: { session.dispatch(commands.setOrientation(state: session.current, orientation: $0)) }
            )
            if advanced {
                DrawingCustomSize(session: session, state: state)
            }
        }
        DrawingSectionTitle(title: "Pattern")
        DrawingChips(
            items: commands.patterns(state: state),
            isSelected: { commands.isPattern(state: state, pattern: $0) },
            label: { commands.patternLabel(pattern: $0) },
            onSelect: { session.dispatch(commands.setPattern(state: session.current, pattern: $0)) }
        )
        if commands.hasPattern(state: state) {
            DrawingSlider(
                label: "Spacing",
                text: commands.patternSpacingText(state: state),
                position: commands.patternSpacingPosition(state: state)
            ) { position in
                session.dispatch(commands.setPatternSpacingPosition(state: session.current, position: position))
            }
        }
        DrawingSectionTitle(title: "Background")
        DrawingFlow(spacing: 0, lineSpacing: 0) {
            ForEach(Array(commands.paperBackgrounds().enumerated()), id: \.offset) { _, color in
                DrawingColorDot(color: color, selected: commands.isBackground(state: state, color: color)) {
                    session.dispatch(commands.setBackground(state: session.current, color: color))
                }
            }
        }
        DrawingSwitchRow(label: "Transparent", isOn: commands.isTransparent(state: state)) { transparent in
            session.dispatch(commands.setTransparent(state: session.current, transparent: transparent))
        }
        DrawingSectionTitle(title: "Texture")
        DrawingChips(
            items: commands.textures(),
            isSelected: { commands.isTexture(state: state, texture: $0) },
            label: { commands.textureLabel(texture: $0) },
            onSelect: { session.dispatch(commands.setTexture(state: session.current, texture: $0)) }
        )
        if advanced {
            DrawingSectionTitle(title: "Units")
            DrawingChips(
                items: commands.units(),
                isSelected: { commands.isUnit(state: state, unit: $0) },
                label: { commands.unitSymbol(unit: $0) },
                onSelect: { session.dispatch(commands.setUnit(state: session.current, unit: $0)) }
            )
            DrawingSectionTitle(title: "Resolution")
            DrawingChips(
                items: commands.dpiChoices(),
                isSelected: { commands.isDpi(state: state, dpi: $0.int32Value) },
                label: { commands.dpiLabel(dpi: $0.int32Value) },
                onSelect: { session.dispatch(commands.setDpi(state: session.current, dpi: $0.int32Value)) }
            )
        }
    }
}

private struct DrawingCustomSize: View {

    let session: DrawingSession
    let state: DrawEditorState

    @Environment(\.colorScheme) private var scheme
    @State private var width = ""
    @State private var height = ""

    var body: some View {
        let commands = DrawCommands.shared
        let palette = SmartTextPalette.of(scheme)
        let unit = commands.paperUnit(state: state)
        HStack(spacing: 8) {
            TextField("Width", text: $width)
                .keyboardType(.decimalPad)
                .textFieldStyle(.roundedBorder)
            TextField("Height", text: $height)
                .keyboardType(.decimalPad)
                .textFieldStyle(.roundedBorder)
            Text(commands.unitSymbol(unit: unit))
                .foregroundColor(palette.onSurfaceMuted)
            Button("Apply") {
                guard let parsedWidth = commands.parseLength(text: width),
                      let parsedHeight = commands.parseLength(text: height) else { return }
                session.dispatch(
                    commands.setCustomSize(
                        state: session.current,
                        width: parsedWidth.floatValue,
                        height: parsedHeight.floatValue,
                        unit: unit
                    )
                )
            }
            .foregroundColor(palette.accent)
        }
        .onChange(of: commands.paper(state: state), initial: true) { _, _ in
            width = commands.lengthText(value: commands.paperWidthIn(state: state))
            height = commands.lengthText(value: commands.paperHeightIn(state: state))
        }
    }
}

private struct DrawingSettingsPanel: View {

    let session: DrawingSession
    let state: DrawEditorState
    let onDismiss: () -> Void

    var body: some View {
        let commands = DrawCommands.shared
        DrawingSectionTitle(title: "Mode")
        DrawingChips(
            items: commands.modes(),
            isSelected: { commands.isMode(state: state, mode: $0) },
            label: { commands.modeLabel(mode: $0) },
            onSelect: { session.dispatch(commands.setMode(mode: $0)) }
        )
        DrawingSwitchRow(label: "Stylus only", isOn: commands.isStylusOnly(state: state)) { enabled in
            session.dispatch(commands.setStylusOnly(enabled: enabled))
        }
        Button("Clear drawing", role: .destructive) {
            session.dispatch(commands.clear())
            onDismiss()
        }
        .disabled(commands.isEmpty(state: state))
    }
}

private struct DrawingSectionTitle: View {

    let title: String
    var detail: String? = nil

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        HStack(spacing: 8) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(palette.onSurface)
            if let detail {
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundColor(palette.onSurfaceMuted)
            }
        }
    }
}

private struct DrawingChips<Item: Hashable>: View {

    let items: [Item]
    let isSelected: (Item) -> Bool
    let label: (Item) -> String
    let onSelect: (Item) -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        DrawingFlow {
            ForEach(items, id: \.self) { item in
                let selected = isSelected(item)
                Button {
                    onSelect(item)
                } label: {
                    Text(label(item))
                        .font(.system(size: 14))
                        .foregroundColor(selected ? palette.onSurface : palette.onSurfaceMuted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(selected ? palette.accentSoft : palette.page))
                        .overlay(Capsule().stroke(selected ? palette.accent : palette.divider, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct DrawingSlider: View {

    let label: String
    let text: String
    let position: Float
    let onChange: (Float) -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label)
                    .font(.system(size: 14))
                    .foregroundColor(palette.onSurface)
                Spacer()
                Text(text)
                    .font(.system(size: 13))
                    .foregroundColor(palette.onSurfaceMuted)
            }
            Slider(value: Binding(get: { Double(position) }, set: { onChange(Float($0)) }), in: 0...1)
                .tint(palette.accent)
        }
    }
}

private struct DrawingSwitchRow: View {

    let label: String
    let isOn: Bool
    let onChange: (Bool) -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        Toggle(isOn: Binding(get: { isOn }, set: onChange)) {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(palette.onSurface)
        }
        .tint(palette.accent)
    }
}

private struct DrawingChoiceCard<Content: View>: View {

    let selected: Bool
    let label: String
    let onTap: () -> Void
    @ViewBuilder let content: () -> Content

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        let shape = RoundedRectangle(cornerRadius: 10)
        Button(action: onTap) {
            VStack(spacing: 4) {
                content()
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                Text(label)
                    .font(.system(size: 11))
                    .lineLimit(1)
                    .foregroundColor(selected ? palette.onSurface : palette.onSurfaceMuted)
            }
            .padding(8)
            .background(shape.fill(selected ? palette.accentSoft : palette.page))
            .overlay(shape.stroke(selected ? palette.accent : palette.divider, lineWidth: selected ? 1.5 : 1))
        }
        .buttonStyle(.plain)
    }
}

private struct DrawingSwatches: View {

    let colors: [DrawColor]
    let state: DrawEditorState
    let onTap: (DrawColor) -> Void
    var onLongPress: ((DrawColor) -> Void)? = nil

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        DrawingFlow(spacing: 0, lineSpacing: 0) {
            ForEach(Array(colors.enumerated()), id: \.offset) { _, color in
                let selected = DrawCommands.shared.isCurrentColor(state: state, color: color)
                Circle()
                    .fill(color.swiftUI)
                    .padding(3)
                    .frame(width: 30, height: 30)
                    .overlay(Circle().stroke(selected ? palette.accent : palette.divider, lineWidth: selected ? 2.5 : 1))
                    .padding(5)
                    .contentShape(Circle())
                    .onTapGesture { onTap(color) }
                    .onLongPressGesture { onLongPress?(color) }
            }
        }
    }
}

struct DrawingFlow: Layout {

    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 4

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let limit = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var line: CGFloat = 0
        var widest: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > limit {
                y += line + lineSpacing
                x = 0
                line = 0
            }
            widest = max(widest, x + size.width)
            x += size.width + spacing
            line = max(line, size.height)
        }
        return CGSize(width: proposal.width ?? widest, height: y + line)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var line: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                y += line + lineSpacing
                x = bounds.minX
                line = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            line = max(line, size.height)
        }
    }
}
