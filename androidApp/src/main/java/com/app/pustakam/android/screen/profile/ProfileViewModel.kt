package com.app.pustakam.android.screen.profile

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.PROFILE
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.displayMessage
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.profile.UpdateProfileReq
import com.app.pustakam.core.model.models.profile.UsernameAvailability
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.core.model.validation.UsernameRules
import com.app.pustakam.feature.auth.domain.profile.CheckUsernameUseCase
import com.app.pustakam.feature.auth.domain.profile.GetMyProfileUseCase
import com.app.pustakam.feature.auth.domain.profile.SetUsernameUseCase
import com.app.pustakam.feature.auth.domain.profile.UpdateProfileUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject

private const val AVAILABILITY_DEBOUNCE_MILLIS = 400L

data class ProfileUiState(
    val user: User? = null,
    val draftName: String = "",
    val draftBio: String = "",
    val draftUsername: String = "",
    val availability: UsernameAvailability? = null,
    val isCheckingUsername: Boolean = false,
    /** 🔧 17-Sep-2026 — the check did not answer. NOT the same as "taken", and never blocks Save. */
    val checkFailed: Boolean = false,
    val isEditingUsername: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val handle: String get() = user?.username?.let { "@$it" }.orEmpty()

    /** 🆔 true while the name was assigned by the server — the prompt to pick a real one. */
    val shouldPromptForUsername: Boolean get() = user?.needsUsername() == true

    val isDiscoverable: Boolean get() = user?.discoverable != false

    /**
     * 🔧 17-Sep-2026 — the button and the request body are computed from the SAME two properties,
     * so they cannot disagree. Before, Save lit up for a change the body then dropped (a cleared
     * name, which the server's min(1) refuses), giving a button that looked live and did nothing.
     *
     * Neither requires a loaded user any more: if the profile fetch failed you could type a bio and
     * Save stayed dead with nothing on screen saying why. An absent user compares as empty fields.
     */
    val changedName: String?
        get() = draftName.trim().takeIf { it.isNotEmpty() && it != user?.name.orEmpty().trim() }

    val changedBio: String?
        get() = draftBio.trim().takeIf { it != user?.bio.orEmpty().trim() }

    val hasUnsavedDetails: Boolean get() = changedName != null || changedBio != null

    /**
     * An answer counts only while it is about the handle on screen right now. The server echoes the
     * canonical username it judged, so a slow reply for an earlier keystroke identifies itself and
     * is ignored instead of unlocking Save for a handle nobody is typing any more.
     */
    private val answerIsForDraft: Boolean
        get() = availability?.username == UsernameRules.canonical(draftUsername)

    private val knownUnavailable: Boolean
        get() = answerIsForDraft && availability?.available == false

    /** Locally invalid input never reaches the network, so the field can explain itself instantly. */
    val usernameHint: String
        get() = when {
            draftUsername.isBlank() -> ""
            !UsernameRules.isWorthChecking(draftUsername) ->
                UsernameAvailability(reason = UsernameRules.rejectionFor(draftUsername)?.name).message()
            isCheckingUsername -> "Checking…"
            answerIsForDraft -> availability?.message().orEmpty()
            checkFailed -> "Couldn't check right now — you can still save and the server will decide."
            else -> ""
        }

    /** Checking and check-failed are both "no verdict yet", so neither may paint the field red. */
    val usernameHintIsNeutral: Boolean get() = isCheckingUsername || checkFailed

    /**
     * 🔧 17-Sep-2026 — Save is enabled unless we KNOW the handle is bad.
     *
     * It used to require `availability?.available == true`, i.e. a SUCCESSFUL round trip to
     * /u/check. One dropped request, one 429 from usernameCheckLimiter, one moment offline, and the
     * button stayed dead forever with no hint on screen, because usernameHint renders "" when
     * availability is null. The server owns uniqueness anyway — a unique index plus a 409 on claim —
     * so the check is a courtesy that fills in the hint, never the thing that grants permission.
     */
    val canSaveUsername: Boolean
        get() = !isSaving && UsernameRules.canSubmit(draftUsername, user?.username, knownUnavailable)
}

/** 👤 The profile screen. Reads through use cases only, like every other ViewModel here. */
class ProfileViewModel : BaseViewModel() {

    private val getMyProfile by inject<GetMyProfileUseCase>()
    private val updateProfile by inject<UpdateProfileUseCase>()
    private val setUsername by inject<SetUsernameUseCase>()
    private val checkUsername by inject<CheckUsernameUseCase>()

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var availabilityJob: Job? = null
    private var hasLoaded = false

    fun load(force: Boolean = false) {
        if (hasLoaded && !force) return
        hasLoaded = true
        _state.update { it.copy(isLoading = true, error = null) }
        makeAWish<User>(PROFILE.USER_PROFILE, showLoader = false) { getMyProfile() }
    }

    // ── details ───────────────────────────────────────────────────────────────

    fun onNameChange(value: String) = _state.update { it.copy(draftName = value) }

    fun onBioChange(value: String) = _state.update { it.copy(draftBio = value) }

    /**
     * 🔧 17-Sep-2026 — sends only the fields that actually changed.
     *
     * It used to send name AND bio every time. updateUserSchema is .strict() and name is
     * `min(1)`, so someone with no name who wrote a bio got a 422 for a field they never touched.
     * explicitNulls = false means a null field is omitted from the body entirely, which is exactly
     * the partial update the schema wants. An empty bio is still sent — the schema allows '' and
     * that is how you clear one.
     */
    fun saveDetails() {
        val current = _state.value
        if (!current.hasUnsavedDetails || current.isSaving) return

        _state.update { it.copy(isSaving = true, error = null) }
        makeAWish<User>(PROFILE.UPDATE, showLoader = false) {
            updateProfile(UpdateProfileReq(name = current.changedName, bio = current.changedBio))
        }
    }

    /**
     * 🔒 The only privacy control, and it governs SEARCH only — an exact handle still resolves, or
     * every QR code the person handed out would break. The UI copy says exactly that.
     */
    fun setDiscoverable(isDiscoverable: Boolean) {
        val current = _state.value
        if (current.user == null || current.isSaving || isDiscoverable == current.isDiscoverable) return
        _state.update { it.copy(isSaving = true, error = null) }
        makeAWish<User>(PROFILE.UPDATE, showLoader = false) {
            updateProfile(UpdateProfileReq(discoverable = isDiscoverable))
        }
    }

    // ── username ──────────────────────────────────────────────────────────────

    fun openUsernameEditor() = _state.update {
        it.copy(isEditingUsername = true, draftUsername = it.user?.username.orEmpty(), availability = null)
    }

    fun closeUsernameEditor() {
        availabilityJob?.cancel()
        _state.update { it.copy(isEditingUsername = false, availability = null, isCheckingUsername = false) }
    }

    /**
     * Debounced, and only for input that could plausibly be valid — the shape check is local, so a
     * hopeless handle never costs a round trip.
     */
    fun onUsernameChange(value: String) {
        availabilityJob?.cancel()
        _state.update {
            it.copy(draftUsername = value, availability = null, isCheckingUsername = false, checkFailed = false)
        }

        if (!checkUsername.isWorthChecking(value)) return
        // canonical on BOTH sides — the stored username keeps its display case
        if (UsernameRules.canonical(value) == UsernameRules.canonical(_state.value.user?.username)) return

        availabilityJob = viewModelScope.launch {
            delay(AVAILABILITY_DEBOUNCE_MILLIS)
            _state.update { it.copy(isCheckingUsername = true) }
            makeAWish(PROFILE.CHECK_USERNAME, showLoader = false) { checkUsername(value) }
        }
    }

    /**
     * The claim can legitimately come back 409 — the availability check is a courtesy, not a gate,
     * so Save is reachable before any answer arrives. baseApiCall discards the error body, so the
     * message cannot yet distinguish "taken" from "renamed too recently" (K31); the sheet shows
     * whatever it does get rather than failing silently, which is what it used to do.
     */
    fun saveUsername() {
        val current = _state.value
        if (!current.canSaveUsername) return
        _state.update { it.copy(isSaving = true, error = null) }
        makeAWish(PROFILE.SET_USERNAME, showLoader = false) { setUsername(current.draftUsername) }
    }

    // ── BaseViewModel ─────────────────────────────────────────────────────────

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        when (taskCode) {
            PROFILE.USER_PROFILE, PROFILE.UPDATE -> applyUser(result.data.data as? User)

            PROFILE.SET_USERNAME -> {
                applyUser(result.data.data as? User)
                _state.update { it.copy(isEditingUsername = false, availability = null, message = "Username updated") }
            }

            PROFILE.CHECK_USERNAME -> _state.update {
                it.copy(availability = result.data.data as? UsernameAvailability, isCheckingUsername = false)
            }

            else -> Unit
        }
    }

    private fun applyUser(user: User?) {
        if (user == null) {
            _state.update { it.copy(isLoading = false, isSaving = false) }
            return
        }
        _state.update {
            it.copy(
                user = user,
                draftName = user.name.orEmpty(),
                draftBio = user.bio.orEmpty(),
                isLoading = false,
                isSaving = false,
                error = null,
            )
        }
    }

    /**
     * 🔧 17-Sep-2026 — a failed /u/check is handled separately from a failed anything-else.
     *
     * The check is a courtesy: it fills in the hint, it does not grant permission. So when it fails
     * — the route missing, a 429 from usernameCheckLimiter, no signal — the screen must not raise an
     * error banner, and Save must stay reachable. What it must NOT do is claim the handle is
     * available: that is a guess the server would contradict one tap later, and the 409 would then
     * arrive with no explanation the user could act on.
     */
    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        if (taskCode == PROFILE.CHECK_USERNAME) {
            _state.update { it.copy(isCheckingUsername = false, checkFailed = true) }
            return
        }
        _state.update {
            it.copy(isLoading = false, isSaving = false, isCheckingUsername = false, error = error.displayMessage())
        }
    }

    override fun clearError() = _state.update { it.copy(error = null, message = null) }
}
