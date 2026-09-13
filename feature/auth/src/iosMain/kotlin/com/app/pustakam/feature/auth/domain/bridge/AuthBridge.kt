package com.app.pustakam.feature.auth.domain.bridge

import com.app.pustakam.core.database.localdb.preferences.UserPreference
import com.app.pustakam.core.model.models.request.Login
import com.app.pustakam.core.model.models.request.RegisterReq
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.feature.auth.domain.usecase.LoginUseCase
import com.app.pustakam.feature.auth.domain.usecase.LogoutUseCase
import com.app.pustakam.core.model.models.profile.PublicUser
import com.app.pustakam.core.model.models.profile.UpdateProfileReq
import com.app.pustakam.core.model.models.profile.UsernameAvailability
import com.app.pustakam.core.network.MediaUpload
import com.app.pustakam.feature.auth.domain.profile.CheckUsernameUseCase
import com.app.pustakam.feature.auth.domain.profile.GetMyProfileUseCase
import com.app.pustakam.feature.auth.domain.profile.GetPublicProfileUseCase
import com.app.pustakam.feature.auth.domain.profile.SearchPeopleUseCase
import com.app.pustakam.feature.auth.domain.profile.SetUsernameUseCase
import com.app.pustakam.feature.auth.domain.profile.UpdateProfileUseCase
import com.app.pustakam.feature.auth.domain.profile.UploadAvatarUseCase
import com.app.pustakam.feature.auth.domain.usecase.SignUseCase
import com.app.pustakam.core.database.localdb.preferences.BasePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import com.app.pustakam.core.common.bridge.BridgeError
import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.data.bridge.subscribeTo
import com.app.pustakam.core.data.bridge.watch

// 🔧 AUTH-FIX: auth entry point for iOS — use-cases only, same pattern as NotesBridge.
//             Replaces LoginHandler/SignUpHandler's direct baseRepositary access and
//             the `as! NetworkError` casting path in Swift.
class AuthBridge : KoinComponent {

    /** Observers: cancelled by dispose() when the screen dies. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        // 🔧 REALTIME-FIX: login/signup/logout are WRITES — they must survive View-struct
        //   recreation and navigation-away (dispose() was able to cancel them mid-flight).
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private val loginUseCase: LoginUseCase by inject()
    private val signUseCase: SignUseCase by inject()
    private val logoutUseCase: LogoutUseCase by inject()

    // 👤 31-Aug-2026 profile — before this, iOS could not reach ANY user CRUD at all
    private val getMyProfileUseCase: GetMyProfileUseCase by inject()
    private val updateProfileUseCase: UpdateProfileUseCase by inject()
    private val uploadAvatarUseCase: UploadAvatarUseCase by inject()
    private val setUsernameUseCase: SetUsernameUseCase by inject()
    private val checkUsernameUseCase: CheckUsernameUseCase by inject()
    private val getPublicProfileUseCase: GetPublicProfileUseCase by inject()
    private val searchPeopleUseCase: SearchPeopleUseCase by inject()

    /** Login with email/phone + password. Repo stores userId + auth flag on success. */
    fun login(
        login: Login,
        onLoading: () -> Unit,
        onSuccess: (User?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(writeScope, { loginUseCase(login) }, onLoading, onSuccess, onError)

    /** Register a new user. */
    fun signup(
        request: RegisterReq,
        onLoading: () -> Unit,
        onSuccess: (User?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(writeScope, { signUseCase(request) }, onLoading, onSuccess, onError)

    /** Clears stored credentials (fixed userLogout underneath). */
    fun logout(onDone: () -> Unit) {
        writeScope.launch {
            logoutUseCase()
            onDone()
        }
    }

    /** Auth state (token/userId/isAuthenticated) — drives auto-login / route guards. */
    fun observeAuthState(onChange: (UserPreference) -> Unit): Closeable =
        // 🔧 30-Jul-2026 02:10 was KoinHelper.getPreference() — that lives in :shared and made :feature:auth -> :shared a cycle; same Koin single, resolved directly
        get<BasePreferences>().userPreferencesFlow.watch(scope) { onChange(it) }

    // ── profile ───────────────────────────────────────────────────────────────
    // Reads use `scope` (cancelled by dispose); writes use `writeScope` so a SwiftUI View struct
    // being recreated cannot cancel a save that is already on its way.

    fun myProfile(
        onLoading: () -> Unit,
        onSuccess: (User?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(scope, { getMyProfileUseCase() }, onLoading, onSuccess, onError)

    fun updateProfile(
        request: UpdateProfileReq,
        onLoading: () -> Unit,
        onSuccess: (User?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(writeScope, { updateProfileUseCase(request) }, onLoading, onSuccess, onError)

    fun uploadAvatar(
        file: MediaUpload,
        onLoading: () -> Unit,
        onSuccess: (User?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(writeScope, { uploadAvatarUseCase(file) }, onLoading, onSuccess, onError)

    fun setUsername(
        username: String,
        onLoading: () -> Unit,
        onSuccess: (User?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(writeScope, { setUsernameUseCase(username) }, onLoading, onSuccess, onError)

    /** 🆔 the local shape check, so a hopeless handle never costs a round trip. */
    fun isUsernameWorthChecking(username: String): Boolean = checkUsernameUseCase.isWorthChecking(username)

    fun localUsernameRejection(username: String): String? =
        checkUsernameUseCase.localRejection(username)?.name

    fun checkUsername(
        username: String,
        onLoading: () -> Unit,
        onSuccess: (UsernameAvailability?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(scope, { checkUsernameUseCase(username) }, onLoading, onSuccess, onError)

    fun publicProfile(
        username: String,
        onLoading: () -> Unit,
        onSuccess: (PublicUser?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(scope, { getPublicProfileUseCase(username) }, onLoading, onSuccess, onError)

    fun searchPeople(
        query: String,
        onLoading: () -> Unit,
        onSuccess: (List<PublicUser>?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(scope, { searchPeopleUseCase(query) }, onLoading, onSuccess, onError)

    fun dispose() = scope.cancel()
}
