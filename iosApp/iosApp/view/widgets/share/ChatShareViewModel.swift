import Foundation
import Combine
import shared

private let noteSearchDebounceSeconds: TimeInterval = 0.3
private let firstPage = 1
private let pageSize = 50

final class ChatShareViewModel: ObservableObject {

    @Published private(set) var state = ChatShareState(
        conversationId: "", query: "", notes: [], pickedId: nil, role: .editor,
        busy: false, sent: false, error: nil
    )

    private let notes: NotesBridgeAdapter
    private let bridge = ShareBridge()
    private var closeables: [Closeable] = []
    private var searchWork: DispatchWorkItem?
    private var latest: [NoteSummary] = []
    private var requestedFirstPage = false
    private var observing = false

    init(notes: NotesBridgeAdapter = NotesBridgeAdapter()) {
        self.notes = notes
    }

    deinit {
        closeables.forEach { $0.close() }
        bridge.dispose()
    }

    func open(conversationId: String) {
        emit(ChatShareIntentOpen(conversationId: conversationId))
        guard !observing else { return emit(ChatShareIntentNotesLoaded(notes: latest)) }
        observing = true
        notes.observeNoteSummaries { [weak self] loaded in
            guard let self else { return }
            self.latest = loaded
            if self.state.query.trimmingCharacters(in: .whitespaces).isEmpty {
                self.emit(ChatShareIntentNotesLoaded(notes: loaded))
            }
            if loaded.isEmpty && !self.requestedFirstPage {
                self.requestedFirstPage = true
                self.notes.getNoteSummaries(page: firstPage, limit: pageSize) { _ in }
            }
        }
    }

    func onQueryChange(_ query: String) {
        searchWork?.cancel()
        emit(ChatShareIntentQueryChanged(query: query))
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            return emit(ChatShareIntentNotesLoaded(notes: latest))
        }
        let work = DispatchWorkItem { [weak self] in
            self?.notes.searchNotes(query: query) { [weak self] result in
                switch result {
                case .success(let list):
                    self?.emit(ChatShareIntentNotesLoaded(notes: (list as? [NoteSummary]) ?? []))
                case .failure(let error):
                    self?.emit(ChatShareIntentFailed(message: error.message))
                case .loading, .idle:
                    break
                }
            }
        }
        searchWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + noteSearchDebounceSeconds, execute: work)
    }

    func pick(_ noteId: String) { emit(ChatShareIntentPicked(noteId: noteId)) }

    func choose(_ role: NoteRole) { emit(ChatShareIntentRoleChosen(role: role)) }

    func send() {
        let current = state
        guard current.canSend, let noteId = current.pickedId else { return }
        emit(ChatShareIntentSending.shared)
        closeables.append(bridge.share(
            noteId: noteId,
            members: [],
            conversationId: current.conversationId,
            role: current.role.name,
            onLoading: {},
            onSuccess: { [weak self] _ in self?.emit(ChatShareIntentSent.shared) },
            onError: { [weak self] error in self?.emit(ChatShareIntentFailed(message: error.message)) }
        ))
    }

    func clearError() { emit(ChatShareIntentErrorCleared.shared) }

    private func emit(_ intent: ChatShareIntent) {
        state = ChatShareReducer.shared.reduce(state: state, intent: intent)
    }
}
