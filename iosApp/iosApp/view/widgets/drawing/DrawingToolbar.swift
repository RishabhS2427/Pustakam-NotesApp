import Combine
import SwiftUI
import shared

struct DrawingChrome: View {

    let session: DrawingSession
    let onDone: () -> Void

    @State private var panel: DrawPanel = DrawCommands.shared.noPanel()

    var body: some View {
        DrawingToolbar(session: session, onPanel: { panel = $0 }, onDone: onDone)
            .id(ObjectIdentifier(session))
            .sheet(isPresented: Binding(
                get: { panel != DrawCommands.shared.noPanel() },
                set: { if !$0 { panel = DrawCommands.shared.noPanel() } }
            )) {
                DrawingPanelSheet(session: session, panel: panel, onDismiss: { panel = DrawCommands.shared.noPanel() })
            }
            .onChange(of: ObjectIdentifier(session)) { _, _ in panel = DrawCommands.shared.noPanel() }
    }
}

struct DrawingToolbar: View {

    let session: DrawingSession
    let onPanel: (DrawPanel) -> Void
    let onDone: () -> Void

    @Environment(\.colorScheme) private var scheme
    @State private var toolbar: DrawToolbarState?

    private var palette: SmartTextPalette { SmartTextPalette.of(scheme) }

    private var commands: DrawCommands { DrawCommands.shared }

    var body: some View {
        let current = toolbar ?? commands.toolbar(state: session.current)
        ViewThatFits(in: .horizontal) {
            row(current)
            ScrollView(.horizontal, showsIndicators: false) { row(current) }
        }
        .background(RoundedRectangle(cornerRadius: 12).fill(palette.toolbar))
        .onReceive(session.$state.map { DrawCommands.shared.toolbar(state: $0) }.removeDuplicates()) { toolbar = $0 }
    }

    private func row(_ current: DrawToolbarState) -> some View {
        HStack(spacing: 2) {
            DrawingToolButton(icon: DrawingIcons.done, label: "Done", tint: palette.accent, action: onDone)
            DrawingToolbarDivider()
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
            DrawingToolbarDivider()
            DrawingToolButton(icon: DrawingIcons.undo, label: "Undo", tint: palette.onSurface, enabled: current.canUndo) {
                session.dispatch(commands.undo())
            }
            DrawingToolButton(icon: DrawingIcons.redo, label: "Redo", tint: palette.onSurface, enabled: current.canRedo) {
                session.dispatch(commands.redo())
            }
            if current.navigates {
                DrawingToolbarDivider()
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
            DrawingToolbarDivider()
            if current.showsPaper {
                DrawingToolButton(icon: DrawingIcons.paper, label: "Paper", tint: palette.onSurface) {
                    onPanel(commands.paperPanel())
                }
            }
            DrawingToolButton(icon: DrawingIcons.settings, label: "Drawing settings", tint: palette.onSurface) {
                onPanel(commands.settingsPanel())
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
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

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Rectangle()
            .fill(SmartTextPalette.of(scheme).divider)
            .frame(width: 1, height: 24)
            .padding(.horizontal, 4)
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
