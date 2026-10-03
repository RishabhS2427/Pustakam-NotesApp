import Foundation
import Combine
import shared

private let peopleSearchDebounceSeconds: TimeInterval = 0.4

final class ShareNoteViewModel: ObservableObject {

    @Published private(set) var state = ShareSheetState(
        noteId: "", query: "", searching: false, results: [], picked: [], role: .editor,
        shares: [], busy: false, sent: false, error: nil
    )

    private let chat: ChatBridgeAdapter
    private let bridge = ShareBridge()
    private var closeables: [Closeable] = []
    private var searchWork: DispatchWorkItem?

    init(chat: ChatBridgeAdapter = ChatBridgeAdapter()) {
        self.chat = chat
    }

    deinit {
        closeables.forEach { $0.close() }
        bridge.dispose()
    }

    func open(noteId: String) {
        emit(ShareSheetIntentOpen(noteId: noteId))
        closeables.append(bridge.shares(
            onLoading: {},
            onSuccess: { [weak self] shares in self?.emit(ShareSheetIntentSharesLoaded(shares: shares ?? [])) },
            onError: { [weak self] error in self?.fail(error) }
        ))
    }

    func onQueryChange(_ query: String) {
        searchWork?.cancel()
        emit(ShareSheetIntentQueryChanged(query: query))
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.chat.peers(query: query) { [weak self] result in
                guard let self else { return }
                switch result {
                case .success(let list):
                    let people = ((list as? [ChatParticipant]) ?? []).map {
                        ShareSheet.shared.candidate(userId: $0.id, name: $0.name, username: $0.username)
                    }
                    let visible = ShareSheet.shared.visible(state: self.state, people: people, me: self.chat.currentUserId)
                    self.emit(ShareSheetIntentResultsLoaded(people: visible))
                case .failure(let error):
                    self.fail(error)
                case .loading, .idle:
                    break
                }
            }
        }
        searchWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + peopleSearchDebounceSeconds, execute: work)
    }

    func pick(_ person: ShareCandidate) { emit(ShareSheetIntentPicked(person: person)) }

    func unpick(_ userId: String) { emit(ShareSheetIntentUnpicked(userId: userId)) }

    func choose(_ role: NoteRole) { emit(ShareSheetIntentRoleChosen(role: role)) }

    func send() {
        let current = state
        guard current.canSend else { return }
        emit(ShareSheetIntentSending.shared)
        closeables.append(bridge.share(
            noteId: current.noteId,
            members: current.members,
            conversationId: nil,
            role: nil,
            onLoading: {},
            onSuccess: { [weak self] share in self?.saved(share) },
            onError: { [weak self] error in self?.fail(error) }
        ))
    }

    func changeRole(shareId: String, userId: String, role: NoteRole) {
        change(shareId: shareId, changes: ShareSheet.shared.roleChange(userId: userId, role: role))
    }

    func remove(shareId: String, userId: String) {
        change(shareId: shareId, changes: ShareSheet.shared.removal(userId: userId))
    }

    func stop(shareId: String) {
        emit(ShareSheetIntentSending.shared)
        closeables.append(bridge.stop(
            shareId: shareId,
            onLoading: {},
            onSuccess: { [weak self] result in
                if let id = result?.shareId { self?.emit(ShareSheetIntentSharingStopped(shareId: id)) }
            },
            onError: { [weak self] error in self?.fail(error) }
        ))
    }

    func clearError() { emit(ShareSheetIntentErrorCleared.shared) }

    private func change(shareId: String, changes: [ShareMemberChange]) {
        emit(ShareSheetIntentSending.shared)
        closeables.append(bridge.changeMembers(
            shareId: shareId,
            changes: changes,
            onLoading: {},
            onSuccess: { [weak self] share in self?.saved(share) },
            onError: { [weak self] error in self?.fail(error) }
        ))
    }

    private func saved(_ share: NoteShare?) {
        guard let share else { return }
        emit(ShareSheetIntentShareSaved(share: share))
    }

    private func fail(_ error: BridgeError) {
        emit(ShareSheetIntentFailed(message: error.message))
    }

    private func emit(_ intent: ShareSheetIntent) {
        state = ShareSheetReducer.shared.reduce(state: state, intent: intent)
    }
}
