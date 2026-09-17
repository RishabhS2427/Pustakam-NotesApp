import Foundation
import shared

/// 👤 Owns the Kotlin AuthBridge for the profile surface. Same lifecycle rules as the other
/// adapters: observers are retained and closed on deinit; writes are deliberately NOT retained,
/// because a SwiftUI View struct being recreated must never cancel a save already on its way.
final class ProfileBridgeAdapter {

    private let bridge = AuthBridge()
    private var closeables: [Closeable] = []

    deinit {
        closeables.forEach { $0.close() }
        bridge.dispose()
    }

    // MARK: - Reads

    func myProfile(onState: @escaping (UiState<User>) -> Void) {
        closeables.append(bridge.myProfile(
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError: { onState(.failure($0)) }
        ))
    }

    /// 🆔 the local shape check, so a hopeless handle never costs a round trip
    func isUsernameWorthChecking(_ username: String) -> Bool {
        bridge.isUsernameWorthChecking(username: username)
    }

    func localUsernameRejection(_ username: String) -> String? {
        bridge.localUsernameRejection(username: username)
    }

    /// 🆔 the one shared rule that decides whether Save is alive — see UsernameRules.canSubmit
    func canSubmitUsername(_ draft: String, current: String?, knownUnavailable: Bool) -> Bool {
        bridge.canSubmitUsername(draft: draft, current: current, knownUnavailable: knownUnavailable)
    }

    func checkUsername(_ username: String, onState: @escaping (UiState<UsernameAvailability>) -> Void) {
        closeables.append(bridge.checkUsername(
            username: username,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError: { onState(.failure($0)) }
        ))
    }

    func searchPeople(query: String, onState: @escaping (UiState<NSArray>) -> Void) {
        closeables.append(bridge.searchPeople(
            query: query,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0 as NSArray?)) },
            onError: { onState(.failure($0)) }
        ))
    }

    // MARK: - Writes (not retained, on purpose)

    func updateProfile(_ request: UpdateProfileReq, onState: @escaping (UiState<User>) -> Void) {
        _ = bridge.updateProfile(
            request: request,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError: { onState(.failure($0)) }
        )
    }

    func setUsername(_ username: String, onState: @escaping (UiState<User>) -> Void) {
        _ = bridge.setUsername(
            username: username,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError: { onState(.failure($0)) }
        )
    }

    func uploadAvatar(_ file: MediaUpload, onState: @escaping (UiState<User>) -> Void) {
        _ = bridge.uploadAvatar(
            file: file,
            onLoading: { onState(.loading) },
            onSuccess: { onState(.success($0)) },
            onError: { onState(.failure($0)) }
        )
    }
}
