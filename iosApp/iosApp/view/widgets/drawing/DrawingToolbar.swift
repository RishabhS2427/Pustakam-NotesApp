import Combine
import SwiftUI
import shared

struct DrawingChrome: View {

    let session: DrawingSession
    let onDone: () -> Void

    @State private var panel: DrawPanel = DrawCommands.shared.noPanel()
    @State private var placement = DrawCommands.shared.toolbarPlacement()
    @State private var measured: CGSize = .zero

    private var commands: DrawCommands { DrawCommands.shared }

    var body: some View {
        GeometryReader { proxy in
            let area = proxy.size
            let fitted = fit(placement, in: area)
            DrawingToolbar(
                session: session,
                vertical: fitted.vertical,
                maxLength: CGFloat(commands.toolbarMaxLength(areaLength: Float(fitted.vertical ? area.height : area.width))),
                onPanel: { panel = $0 },
                onDone: onDone,
                onMove: { deltaX, deltaY in move(deltaX: deltaX, deltaY: deltaY, in: area) },
                onRotate: { placement = commands.rotateToolbar(placement: fitted) }
            )
            .id(ObjectIdentifier(session))
            .background(
                GeometryReader { toolbar in
                    Color.clear.onChange(of: toolbar.size, initial: true) { _, size in measured = size }
                }
            )
            .offset(
                x: CGFloat(fitted.left(width: Float(measured.width))),
                y: CGFloat(fitted.top(height: Float(measured.height)))
            )
            .opacity(fitted.placed ? 1 : 0)
        }
        .sheet(isPresented: Binding(
            get: { panel != DrawCommands.shared.noPanel() },
            set: { if !$0 { panel = DrawCommands.shared.noPanel() } }
        )) {
            DrawingPanelSheet(session: session, panel: panel, onDismiss: { panel = DrawCommands.shared.noPanel() })
        }
        .onChange(of: ObjectIdentifier(session)) { _, _ in panel = DrawCommands.shared.noPanel() }
    }

    private func fit(_ value: DrawToolbarPlacement, in area: CGSize) -> DrawToolbarPlacement {
        commands.fitToolbar(
            placement: value,
            width: Float(measured.width),
            height: Float(measured.height),
            areaWidth: Float(area.width),
            areaHeight: Float(area.height)
        )
    }

    private func move(deltaX: CGFloat, deltaY: CGFloat, in area: CGSize) {
        placement = commands.moveToolbar(
            placement: fit(placement, in: area),
            deltaX: Float(deltaX),
            deltaY: Float(deltaY),
            width: Float(measured.width),
            height: Float(measured.height),
            areaWidth: Float(area.width),
            areaHeight: Float(area.height)
        )
    }
}

struct DrawingToolbar: View {

    let session: DrawingSession
    let vertical: Bool
    let maxLength: CGFloat
    let onPanel: (DrawPanel) -> Void
    let onDone: () -> Void
    let onMove: (CGFloat, CGFloat) -> Void
    let onRotate: () -> Void

    @Environment(\.colorScheme) private var scheme
    @State private var toolbar: DrawToolbarState?
    @State private var dragged: CGSize = .zero
    @GestureState private var dragging = false

    private var commands: DrawCommands { DrawCommands.shared }

    var body: some View {
        let colors = SmartTextPalette.of(scheme)
        let current = toolbar ?? commands.toolbar(state: session.current)
        DrawingToolbarStrip(vertical: vertical, maxLength: maxLength) {
            Image(systemName: DrawingIcons.move)
                .font(.system(size: 16))
                .foregroundColor(colors.onSurfaceMuted)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
                .gesture(moveGesture)
                .accessibilityLabel(Text("Move toolbar"))
            DrawingToolButton(icon: DrawingIcons.turn, label: "Turn toolbar", tint: colors.onSurface, action: onRotate)
            DrawingToolButton(icon: DrawingIcons.done, label: "Done", tint: colors.accent, action: onDone)
            DrawingToolbarDivider(vertical: vertical)
            ForEach(current.tools, id: \.id) { spec in
                DrawingToolButton(
                    icon: DrawingIcons.tool(spec.iconKey),
                    label: spec.label,
                    tint: current.isTool(value: spec.id) ? colors.accent : colors.onSurface
                ) {
                    if commands.opensPanel(state: session.current, tool: spec.id) {
                        onPanel(commands.panelFor(tool: spec.id))
                    } else {
                        session.dispatch(commands.setTool(tool: spec.id))
                    }
                }
            }
            DrawingColorDot(color: current.color) { onPanel(commands.colorPanel()) }
            if current.navigates {
                DrawingToolbarDivider(vertical: vertical)
                DrawingToolButton(icon: DrawingIcons.zoomOut, label: "Zoom out", tint: colors.onSurface) {
                    session.dispatch(commands.zoomOut())
                }
                Text("\(current.zoomPercent)%")
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceMuted)
                DrawingToolButton(icon: DrawingIcons.zoomIn, label: "Zoom in", tint: colors.onSurface) {
                    session.dispatch(commands.zoomIn())
                }
                DrawingToolButton(icon: DrawingIcons.fit, label: "Fit", tint: colors.onSurface) {
                    session.dispatch(commands.fitPaper())
                }
                if current.rotated {
                    DrawingToolButton(icon: DrawingIcons.resetRotation, label: "Reset rotation", tint: colors.onSurface) {
                        session.dispatch(commands.resetRotation())
                    }
                }
            }
            DrawingToolbarDivider(vertical: vertical)
            if current.showsPaper {
                DrawingToolButton(icon: DrawingIcons.paper, label: "Paper", tint: colors.onSurface) {
                    onPanel(commands.paperPanel())
                }
            }
            DrawingToolButton(icon: DrawingIcons.settings, label: "Drawing settings", tint: colors.onSurface) {
                onPanel(commands.settingsPanel())
            }
        }
        .scrollDisabled(dragging)
        .onChange(of: dragging) { _, active in
            if !active { dragged = .zero }
        }
        .onReceive(session.$state.map { DrawCommands.shared.toolbar(state: $0) }.removeDuplicates()) { toolbar = $0 }
    }

    private var moveGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .updating($dragging) { _, active, _ in active = true }
            .onChanged { value in
                onMove(value.translation.width - dragged.width, value.translation.height - dragged.height)
                dragged = value.translation
            }
    }
}

private struct DrawingToolbarStrip<Content: View>: View {

    let vertical: Bool
    let maxLength: CGFloat
    @ViewBuilder let content: () -> Content

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        DrawingToolbarLength(vertical: vertical, maxLength: maxLength) {
            ViewThatFits(in: vertical ? .vertical : .horizontal) {
                strip
                ScrollView(vertical ? .vertical : .horizontal, showsIndicators: false) { strip }
            }
            .background(RoundedRectangle(cornerRadius: 12).fill(SmartTextPalette.of(scheme).toolbar))
        }
        .fixedSize(horizontal: vertical, vertical: !vertical)
    }

    private var strip: some View {
        let layout = vertical ? AnyLayout(VStackLayout(spacing: 2)) : AnyLayout(HStackLayout(spacing: 2))
        return layout { content() }
            .padding(.horizontal, vertical ? 2 : 6)
            .padding(.vertical, vertical ? 6 : 2)
    }
}

private struct DrawingToolbarLength: Layout {

    let vertical: Bool
    let maxLength: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        subviews.first?.sizeThatFits(capped(proposal)) ?? .zero
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: bounds.origin, anchor: .topLeading, proposal: capped(proposal))
    }

    private func capped(_ proposal: ProposedViewSize) -> ProposedViewSize {
        if vertical {
            return ProposedViewSize(width: proposal.width, height: min(proposal.height ?? maxLength, maxLength))
        }
        return ProposedViewSize(width: min(proposal.width ?? maxLength, maxLength), height: proposal.height)
    }
}

struct DrawingHistoryButtons: View {

    @ObservedObject var session: DrawingSession
    let tint: Color

    var body: some View {
        let commands = DrawCommands.shared
        let state = session.state
        HStack(spacing: 4) {
            DrawingToolButton(icon: DrawingIcons.undo, label: "Undo", tint: tint, enabled: commands.canUndo(state: state)) {
                session.dispatch(commands.undo())
            }
            DrawingToolButton(icon: DrawingIcons.redo, label: "Redo", tint: tint, enabled: commands.canRedo(state: state)) {
                session.dispatch(commands.redo())
            }
        }
    }
}

struct DrawingToolButton: View {

    let icon: String
    let label: String
    let tint: Color
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(enabled ? tint : tint.opacity(0.35))
                .frame(width: 40, height: 40)
        }
        .disabled(!enabled)
        .accessibilityLabel(Text(label))
    }
}

struct DrawingToolbarDivider: View {

    var vertical: Bool = false

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Rectangle()
            .fill(SmartTextPalette.of(scheme).divider)
            .frame(width: vertical ? 24 : 1, height: vertical ? 1 : 24)
            .padding(vertical ? .vertical : .horizontal, 4)
    }
}

struct DrawingColorDot: View {

    let color: DrawColor
    var selected: Bool = false
    var diameter: CGFloat = 28
    let action: () -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = SmartTextPalette.of(scheme)
        Button(action: action) {
            Circle()
                .fill(color.swiftUI)
                .padding(3)
                .frame(width: diameter, height: diameter)
                .overlay(
                    Circle().stroke(selected ? palette.accent : palette.divider, lineWidth: selected ? 2.5 : 1)
                )
                .padding(6)
        }
        .buttonStyle(.plain)
    }
}
