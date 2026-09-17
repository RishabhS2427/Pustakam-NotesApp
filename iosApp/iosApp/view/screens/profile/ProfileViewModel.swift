import Foundation
import Combine
import shared

private let availabilityDebounceSeconds: TimeInterval = 0.4

/// 👤 Your own profile, iOS side. Field-for-field the same state Android's ProfileViewModel holds,
/// so the two screens cannot drift apart.
final class ProfileViewModel: ObservableObject {

    @Published private(set) var user: User?
    @Published var draftName: String = ""
    @Published var draftBio: String = ""
    @Published var draftUsername: String = ""
    @Published private(set) var availability: UsernameAvailability?
    @Published private(set) var isCheckingUsername = false
    /// 🔧 17-Sep-2026 — the check did not answer. NOT the same as "taken", and never blocks Save.
    @Published private(set) var checkFailed = false
    @Published var isEditingUsername = false
    @Published private(set) var isLoading = false
    @Published private(set) var isSaving = false
    @Published private(set) var error: String?
    @Published private(set) var message: String?

    private let adapter: ProfileBridgeAdapter
    private var availabilityWork: DispatchWorkItem?
    private var hasLoaded = false

    init(adapter: ProfileBridgeAdapter = ProfileBridgeAdapter()) {
        self.adapter = adapter
    }

    // MARK: - Derived state

    var handle: String {
        guard let username = user?.username, !username.isEmpty else { return "" }
        return "@\(username)"
    }

    /// 🆔 true while the name was assigned by the server — the prompt to pick a real one.
    var shouldPromptForUsername: Bool { user?.needsUsername() ?? false }

    var isDiscoverable: Bool { user?.discoverable ?? true }

    var displayName: String { user?.displayName() ?? "" }

    var initial: String { user?.initial() ?? "?" }

    var avatarUrl: String? { user?.avatarUrl.absoluteMediaUrl }

    /// 🔧 17-Sep-2026 — the button and the request body are computed from the SAME two properties,
    /// so they cannot disagree. Before, Save lit up for a change the body then dropped (a cleared
    /// name, which the server's min(1) refuses), giving a button that looked live and did nothing.
    ///
    /// Neither requires a loaded user any more: if the profile fetch failed you could type a bio and
    /// Save stayed dead with nothing on screen saying why. An absent user compares as empty fields.
    var changedName: String? {
        let value = draftName.trimmed
        return (!value.isEmpty && value != (user?.name ?? "").trimmed) ? value : nil
    }

    var changedBio: String? {
        let value = draftBio.trimmed
        return value != (user?.bio ?? "").trimmed ? value : nil
    }

    var hasUnsavedDetails: Bool { changedName != nil || changedBio != nil }

    /// An answer counts only while it is about the handle on screen right now. The server echoes the
    /// canonical username it judged, so a slow reply for an earlier keystroke identifies itself and
    /// is ignored instead of unlocking Save for a handle nobody is typing any more.
    private var answerIsForDraft: Bool {
        availability?.username == canonical(draftUsername)
    }

    private var knownUnavailable: Bool {
        answerIsForDraft && availability?.available == false
    }

    /// Locally invalid input never reaches the network, so the field can explain itself instantly.
    var usernameHint: String {
        if draftUsername.isEmpty { return "" }
        if !adapter.isUsernameWorthChecking(draftUsername) {
            return UsernameAvailability(
                username: draftUsername,
                available: false,
                reason: adapter.localUsernameRejection(draftUsername),
                suggestions: []
            ).message()
        }
        if isCheckingUsername { return "Checking…" }
        if answerIsForDraft { return availability?.message() ?? "" }
        if checkFailed { return "Couldn't check right now — you can still save and the server will decide." }
        return ""
    }

    /// Checking and check-failed are both "no verdict yet", so neither may paint the field red.
    var usernameHintIsNeutral: Bool { isCheckingUsername || checkFailed }

    var isUsernameAvailable: Bool { answerIsForDraft && availability?.available == true }

    var suggestions: [String] { answerIsForDraft ? (availability?.suggestions ?? []) : [] }

    /// 🔧 17-Sep-2026 — Save is enabled unless we KNOW the handle is bad.
    ///
    /// It used to require a SUCCESSFUL round trip to /u/check. One dropped request, one 429 from
    /// usernameCheckLimiter, one moment offline, and the button stayed dead forever with no hint on
    /// screen, because usernameHint renders "" when there is no answer. The server owns uniqueness
    /// anyway — a unique index plus a 409 on claim — so the check fills in the hint, it does not
    /// grant permission.
    var canSaveUsername: Bool {
        !isSaving && adapter.canSubmitUsername(
            draftUsername, current: user?.username, knownUnavailable: knownUnavailable
        )
    }

    // MARK: - Load

    func load(force: Bool = false) {
        if hasLoaded && !force { return }
        hasLoaded = true
        isLoading = true
        error = nil
        adapter.myProfile { [weak self] state in
            self?.onMain { self?.handleUser(state) }
        }
    }

    // MARK: - Details

    func onNameChange(_ value: String) { draftName = value }

    func onBioChange(_ value: String) { draftBio = value }

    /// 🔧 17-Sep-2026 — sends only the fields that actually changed.
    ///
    /// It used to send name AND bio every time. updateUserSchema is .strict() and name is min(1),
    /// so someone with no name who wrote a bio got a 422 for a field they never touched. A nil
    /// field is omitted from the body entirely (explicitNulls = false), which is exactly the
    /// partial update the schema wants. An empty bio is still sent — that is how you clear one.
    func saveDetails() {
        guard hasUnsavedDetails, !isSaving else { return }

        isSaving = true
        error = nil
        let request = UpdateProfileReq(name: changedName, bio: changedBio, discoverable: nil)
        adapter.updateProfile(request) { [weak self] state in
            self?.onMain { self?.handleUser(state) }
        }
    }

    /// 🔒 The only privacy control, and it governs SEARCH only — an exact handle still resolves, or
    /// every QR code the person handed out would break. The UI copy says exactly that.
    ///
    /// The no-op guard is load-bearing on iOS: SettingsRow seeds its Toggle in onAppear, which
    /// fires onToggle with the value it was just given — without this, opening the screen would
    /// PATCH the server every time.
    func setDiscoverable(_ isOn: Bool) {
        guard user != nil, isOn != isDiscoverable, !isSaving else { return }
        isSaving = true
        error = nil
        let request = UpdateProfileReq(name: nil, bio: nil, discoverable: KotlinBoolean(bool: isOn))
        adapter.updateProfile(request) { [weak self] state in
            self?.onMain { self?.handleUser(state) }
        }
    }

    // MARK: - Username

    func openUsernameEditor() {
        draftUsername = user?.username ?? ""
        availability = nil
        isEditingUsername = true
    }

    func closeUsernameEditor() {
        availabilityWork?.cancel()
        availabilityWork = nil
        isEditingUsername = false
        availability = nil
        isCheckingUsername = false
    }

    /// Debounced, and only for input that could plausibly be valid — the shape check is local, so a
    /// hopeless handle never costs a round trip.
    func onUsernameChange(_ value: String) {
        availabilityWork?.cancel()
        draftUsername = value
        availability = nil
        isCheckingUsername = false
        checkFailed = false

        guard adapter.isUsernameWorthChecking(value) else { return }
        // canonical on BOTH sides — the stored username keeps its display case
        guard canonical(value) != canonical(user?.username ?? "") else { return }

        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.isCheckingUsername = true
            self.adapter.checkUsername(value) { [weak self] state in
                self?.onMain { self?.handleAvailability(state) }
            }
        }
        availabilityWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + availabilityDebounceSeconds, execute: work)
    }

    /// Availability is always checked before saving, so a 409 here is only ever a lost race —
    /// which matters because baseApiCall discards the error body, leaving "taken" and "cooldown"
    /// indistinguishable on the client. Logged as a known issue.
    func saveUsername() {
        guard canSaveUsername else { return }
        isSaving = true
        error = nil
        adapter.setUsername(draftUsername) { [weak self] state in
            self?.onMain { self?.handleUsernameSaved(state) }
        }
    }

    func clearError() {
        error = nil
        message = nil
    }

    // MARK: - Results

    private func handleUser(_ state: UiState<User>) {
        switch state {
        case .success(let value): apply(value)
        case .failure(let failure): fail(failure.message)
        case .loading, .idle: break
        }
    }

    private func handleUsernameSaved(_ state: UiState<User>) {
        switch state {
        case .success(let value):
            apply(value)
            isEditingUsername = false
            availability = nil
            message = "Username updated"
        case .failure(let failure):
            fail(failure.message)
        case .loading, .idle:
            break
        }
    }

    /// 🔧 17-Sep-2026 — a failed /u/check is a missing courtesy, not an error worth a banner.
    ///
    /// The check fills in the hint; it does not grant permission. When it fails — the route
    /// missing, a 429 from usernameCheckLimiter, no signal — Save stays reachable and the field
    /// says so. What it must NOT do is claim the handle is available: that is a guess the server
    /// would contradict one tap later, and the 409 would arrive with nothing the user could act on.
    private func handleAvailability(_ state: UiState<UsernameAvailability>) {
        switch state {
        case .success(let value):
            availability = value
            isCheckingUsername = false
            checkFailed = false
        case .failure:
            isCheckingUsername = false
            checkFailed = true
        case .loading, .idle:
            break
        }
    }

    private func apply(_ value: User?) {
        guard let value else {
            isLoading = false
            isSaving = false
            return
        }
        user = value
        draftName = value.name ?? ""
        draftBio = value.bio ?? ""
        isLoading = false
        isSaving = false
        error = nil
    }

    private func fail(_ reason: String?) {
        isLoading = false
        isSaving = false
        isCheckingUsername = false
        error = reason
    }

    private func canonical(_ value: String) -> String { value.trimmed.lowercased() }

    private func onMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread { block() } else { DispatchQueue.main.async(execute: block) }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
