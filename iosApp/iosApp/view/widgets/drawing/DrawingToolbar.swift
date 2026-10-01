import Combine
import SwiftUI
import shared

struct DrawingChrome: View {

    let session: DrawingSession
    let onDone: () -> Void

    @State private var panel: DrawPanel = DrawCommands.shared.noPanel()
    @State private var placement = DrawCommands.shared.toolbarPlacement()
    @State private var dragStart: DrawToolbarPlacement?
    @State private var size: CGSize = .zero

    private var commands: DrawCommands { DrawCommands.shared }

    var body: some View {
        GeometryReader { proxy in
            let area = proxy.size
            let fitted = fit(placement, in: area)
            DrawingToolbar(
                session: session,
                vertical: fitted.vertical,
                onPanel: { panel = $0 },
                onDone: onDone,
                onMove: { move(by: $0, in: area) },
                onMoveEnd: { dragStart = nil },
                onTurn: { placement = commands.rotateToolbar(placement: fitted) }
            )
            .id(ObjectIdentifier(session))
            .background(
                GeometryReader { toolbar in
                    Color.clear.preference(key: DrawingToolbarSizeKey.self, value: toolbar.size)
                }
            )
            .onPreferenceChange(DrawingToolbarSizeKey.self) { size = $0 }
            .frame(
                width: fitted.vertical ? nil : length(area.width),
                height: fitted.vertical ? length(area.height) : nil,
                alignment: .topLeading
            )
            .offset(
                x: CGFloat(fitted.left(width: Float(size.width))),
                y: CGFloat(fitted.top(height: Float(size.height)))
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

    private func length(_ area: CGFloat) -> CGFloat {
        CGFloat(commands.toolbarMaxLength(areaLength: Float(area)))
    }

    private func fit(_ value: DrawToolbarPlacement, in area: CGSize) -> DrawToolbarPlacement {
        commands.fitToolbar(
            placement: value,
            width: Float(size.width),
            height: Float(size.height),
            areaWidth: Float(area.width),
            areaHeight: Float(area.height)
        )
    }

    private func move(by translation: CGSize, in area: CGSize) {
        let start = dragStart ?? fit(placement, in: area)
        dragStart = start
        placement = commands.moveToolbar(
            placement: start,
            deltaX: Float(translation.width),
            deltaY: Float(translation.height),
            width: Float(size.width),
            height: Float(size.height),
            areaWidth: Float(area.width),
            areaHeight: Float(area.height)
        )
    }
}

private struct DrawingToolbarSizeKey: PreferenceKey {
    static var defaultValue: CGSize = .zero

    static func reduce(value: inout CGSize, nextValue: () -> CGSize) {
        value = nextValue()
    }
}

struct DrawingToolbar: View {

    let session: DrawingSession
    var vertical: Bool = false
    let onPanel: (DrawPanel) -> Void
    let onDone: () -> Void
    var onMove: (CGSize) -> Void = { _ in }
    var onMoveEnd: () -> Void = {}
    var onTurn: () -> Void = {}

    @Environment(\.colorScheme) private var scheme
    @State private var toolbar: DrawToolbarState?

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    private var commands: DrawCommands { DrawCommands.shared }

    var body: some View {
        let current = toolbar ?? commands.toolbar(state: session.current)
        ViewThatFits(in: vertical ? .vertical : .horizontal) {
            strip(current)
            ScrollView(vertical ? .vertical : .horizontal, showsIndicators: false) { strip(current) }
        }
        .fixedSize(horizontal: vertical, vertical: !vertical)
        .background(RoundedRectangle(cornerRadius: 12).fill(palette.toolbar))
        .onReceive(session.$state.map { DrawCommands.shared.toolbar(state: $0) }.removeDuplicates()) { toolbar = $0 }
    }

    private func strip(_ current: DrawToolbarState) -> some View {
        let layout = vertical ? AnyLayout(VStackLayout(spacing: 2)) : AnyLayout(HStackLayout(spacing: 2))
        return layout {
            Image(systemName: DrawingIcons.move)
                .font(.system(size: 16))
                .foregroundColor(palette.onSurfaceMuted)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(coordinateSpace: .global)
                        .onChanged { onMove($0.translation) }
                        .onEnded { _ in onMoveEnd() }
                )
                .accessibilityLabel(Text("Move toolbar"))
            DrawingToolButton(icon: DrawingIcons.turn, label: "Turn toolbar", tint: palette.onSurface, action: onTurn)
            DrawingToolButton(icon: DrawingIcons.done, label: "Done", tint: palette.accent, action: onDone)
            DrawingToolbarDivider(vertical: vertical)
            ForEach(current.tools, id: \.id) { spec in
                DrawingToolButton(
                    icon: DrawingIcons.tool(spec.iconKey),
                    label: spec.label,
                    tint: current.isTool(value: spec.id) ? palette.accent : palette.onSurface
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
                DrawingToolButton(icon: DrawingIcons.zoomOut, label: "Zoom out", tint: palette.onSurface) {
                    session.dispatch(commands.zoomOut())
                }
                Text("\(current.zoomPercent)%")
                    .font(.system(size: 13))
                    .foregroundColor(palette.onSurfaceMuted)
                DrawingToolButton(icon: DrawingIcons.zoomIn, label: "Zoom in", tint: palette.onSurface) {
                    session.dispatch(commands.zoomIn())
                }
                DrawingToolButton(icon: DrawingIcons.fit, label: "Fit", tint: palette.onSurface) {
                    session.dispatch(commands.fitPaper())
                }
                if current.rotated {
                    DrawingToolButton(icon: DrawingIcons.resetRotation, label: "Reset rotation", tint: palette.onSurface) {
                        session.dispatch(commands.resetRotation())
                    }
                }
            }
            DrawingToolbarDivider(vertical: vertical)
            if current.showsPaper {
                DrawingToolButton(icon: DrawingIcons.paper, label: "Paper", tint: palette.onSurface) {
                    onPanel(commands.paperPanel())
                }
            }
            DrawingToolButton(icon: DrawingIcons.settings, label: "Drawing settings", tint: palette.onSurface) {
                onPanel(commands.settingsPanel())
            }
        }
        .padding(.horizontal, vertical ? 2 : 6)
        .padding(.vertical, vertical ? 6 : 2)
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
