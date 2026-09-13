import Foundation
import Combine
import shared

/// 💬 The app-wide chat session, mirroring Android's AppViewModel.
///
/// Chat runs for the whole signed-in app rather than only while its tab is on screen — otherwise a
/// message only arrives once you go looking for it, and the tab badge never lights up while you are
/// writing a note.
final class ChatSessionModel: ObservableObject {

    @Published private(set) var totalUnread: Int = 0

    private let adapter = ChatBridgeAdapter()

    init() {
        adapter.observeTotalUnread { [weak self] total in
            DispatchQueue.main.async { self?.totalUnread = total }
        }
    }

    func start() { adapter.start() }

    func stop() { adapter.stop() }

    func setForeground(_ isForeground: Bool) { adapter.setAppForeground(isForeground) }
}
