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
    var shouldPromptForUsername: Bool { user.map { UserKt.needsUsername($0) } ?? false }

    var isDiscoverable: Bool { user?.discoverable ?? true }

    var displayName: String { user.map { UserKt.displayName($0) } ?? "" }

    var initial: String { user.map { UserKt.initial($0) } ?? "?" }

    var avatarUrl: String? { user?.avatarUrl.absoluteMediaUrl }

    var hasUnsavedDetails: Bool {
        guard let user else { return false }
        return draftName != (user.name ?? "") || draftBio != (user.bio ?? "")
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
        return availability?.message() ?? ""
    }

    var isUsernameAvailable: Bool { availability?.available == true }

    var suggestions: [String] { availability?.suggestions ?? [] }

    var canSaveUsername: Bool {
        !isSaving && availability?.available == true && canonical(draftUsername) != user?.username
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

    func saveDetails() {
        guard hasUnsavedDetails, !isSaving else { return }
        isSaving = true
        error = nil
        let request = UpdateProfileReq(
            name: draftName.trimmingCharacters(in: .whitespacesAndNewlines),
            bio: draftBio.trimmingCharacters(in: .whitespacesAndNewlines),
            discoverable: nil
        )
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

        guard adapter.isUsernameWorthChecking(value) else { return }
        guard canonical(value) != user?.username else { return }

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

    private func handleAvailability(_ state: UiState<UsernameAvailability>) {
        switch state {
        case .success(let value):
            availability = value
            isCheckingUsername = false
        case .failure(let failure):
            isCheckingUsername = false
            error = failure.message
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

    private func canonical(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private func onMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread { block() } else { DispatchQueue.main.async(execute: block) }
    }
}
