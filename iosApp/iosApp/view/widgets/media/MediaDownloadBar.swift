import SwiftUI
import shared

// 📥⬆️ the Swift side of one card's transfer strip — every word it shows comes out of shared Kotlin
final class MediaDownloadModel: ObservableObject {

    @Published private(set) var ui: MediaTransferUi?

    private let bridge = MediaDownloadBridge()
    private var media: NoteContentModel.MediaContent
    private var closeable: Closeable?

    init(media: NoteContentModel.MediaContent) {
        self.media = media
        self.ui = nil
    }

    deinit {
        closeable?.close()
        bridge.dispose()
    }

    // 📥 re-pointable: a recycled row, a landed download or a finished upload all hand in a new block
    func bind(to newMedia: NoteContentModel.MediaContent) {
        if closeable != nil && MediaDownloadModel.key(newMedia) == MediaDownloadModel.key(media) { return }
        closeable?.close()
        media = newMedia
        ui = bridge.currentTransferUi(content: newMedia)
        closeable = bridge.observeTransfer(content: newMedia) { [weak self] value in
            self?.onMain { self?.ui = value }
        }
    }

    // ⏯️ the ONE action behind the button — the state machine lives in shared Kotlin
    func toggle() { bridge.toggle(content: media) }

    func cancel() { bridge.cancel(content: media) }

    // 📥 what a strip depends on: which block, and whether its bytes are here or on the server
    static func key(_ media: NoteContentModel.MediaContent) -> String {
        "\(media.id)|\(media.localPath ?? "")|\(media.assetId ?? "")"
    }

    private func onMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread { block() } else { DispatchQueue.main.async(execute: block) }
    }
}

// 📥⬆️ THE generic media transfer widget — bar, percent/ETA, done/left, one button, failures in red
struct MediaDownloadBar: View {

    let ui: MediaTransferUi
    var onToggle: () -> Void = {}
    var onCancel: (() -> Void)?

    // ❗ an error is drawn in the error colour so it reads as a problem, not a status
    private var accent: Color { ui.isError ? Theme.Colors.error : Theme.Colors.secondary }

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                // ⏯️ pause / resume / download / retry — absent while an upload is simply running
                if ui.buttonAction != MediaButtonAction.none {
                    Image(systemName: buttonSymbol)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Theme.Colors.ivory)
                        .frame(width: 26, height: 26)
                        .background(Circle().fill(accent))
                        .contentShape(Circle())
                        .onTapGesture { onToggle() }
                        .accessibilityLabel(ui.buttonAction.label())
                }

                // 📥 centre: percentage and ETA, or what went wrong
                Text(ui.centerLabel)
                    .font(.caption)
                    .foregroundColor(ui.isError ? Theme.Colors.error : Theme.Colors.ivory)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)

                // 📥 end: what landed and what is left
                if !ui.edgeLabel.isEmpty {
                    Text(ui.edgeLabel)
                        .font(.caption2)
                        .foregroundColor(Theme.Colors.ivory)
                        .lineLimit(1)
                }

                // 🗑️ only worth offering once there are bytes to throw away
                if let onCancel, ui.canCancel {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Theme.Colors.ivory)
                        .contentShape(Rectangle())
                        .onTapGesture { onCancel() }
                        .accessibilityLabel("Cancel download")
                }
            }

            // 📥 the bar itself, across the whole width of the card
            if ui.showsBar {
                if ui.isIndeterminate {
                    ProgressView()
                        .progressViewStyle(.linear)
                        .tint(accent)
                } else {
                    ProgressView(value: Double(ui.fraction))
                        .progressViewStyle(.linear)
                        .tint(accent)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(Color.black.opacity(0.55))
    }

    // ⏯️ a Kotlin enum is a CLASS in Swift, not a Swift enum — compare, never `switch case .x`
    private var buttonSymbol: String {
        let action = ui.buttonAction
        if action == MediaButtonAction.pause { return "pause.fill" }
        if action == MediaButtonAction.resume { return "play.fill" }
        if action == MediaButtonAction.retry { return "arrow.clockwise" }
        return "arrow.down"
    }
}

// 📥 the drop-in for a card: draws nothing once the bytes are here and nothing is wrong
struct MediaDownloadOverlay: View {

    private let media: NoteContentModel.MediaContent
    @StateObject private var model: MediaDownloadModel

    init(media: NoteContentModel.MediaContent) {
        self.media = media
        _model = StateObject(wrappedValue: MediaDownloadModel(media: media))
    }

    var body: some View {
        Group {
            if let ui = model.ui {
                MediaDownloadBar(
                    ui: ui,
                    onToggle: { model.toggle() },
                    onCancel: { model.cancel() }
                )
            } else {
                // 📥 not EmptyView: .onAppear never fires on one, so the model never bound and iOS showed nothing
                Color.clear.frame(height: 0)
            }
        }
        .onAppear { model.bind(to: media) }
        // 📥 a recycled row, a landed download or a finished upload — re-point the model
        .onChange(of: MediaDownloadModel.key(media)) { _, _ in model.bind(to: media) }
    }
}
