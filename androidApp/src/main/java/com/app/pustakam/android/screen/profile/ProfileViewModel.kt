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

    val hasUnsavedDetails: Boolean
        get() = user != null && (draftName != user.name.orEmpty() || draftBio != user.bio.orEmpty())

    /** Locally invalid input never reaches the network, so the field can explain itself instantly. */
    val usernameHint: String
        get() = when {
            draftUsername.isBlank() -> ""
            !UsernameRules.isWorthChecking(draftUsername) ->
                UsernameAvailability(reason = UsernameRules.rejectionFor(draftUsername)?.name).message()
            isCheckingUsername -> "Checking…"
            else -> availability?.message().orEmpty()
        }

    val canSaveUsername: Boolean
        get() = !isSaving && availability?.available == true &&
            UsernameRules.canonical(draftUsername) != user?.username
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

    fun saveDetails() {
        val current = _state.value
        if (!current.hasUnsavedDetails) return
        _state.update { it.copy(isSaving = true, error = null) }
        makeAWish<User>(PROFILE.UPDATE, showLoader = false) {
            updateProfile(UpdateProfileReq(name = current.draftName.trim(), bio = current.draftBio.trim()))
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
        _state.update { it.copy(draftUsername = value, availability = null, isCheckingUsername = false) }

        if (!checkUsername.isWorthChecking(value)) return
        if (UsernameRules.canonical(value) == _state.value.user?.username) return

        availabilityJob = viewModelScope.launch {
            delay(AVAILABILITY_DEBOUNCE_MILLIS)
            _state.update { it.copy(isCheckingUsername = true) }
            makeAWish<UsernameAvailability>(PROFILE.CHECK_USERNAME, showLoader = false) { checkUsername(value) }
        }
    }

    /**
     * Availability is always checked before saving, so a 409 here is only ever a lost race —
     * which matters because baseApiCall discards the error body, leaving "taken" and "cooldown"
     * indistinguishable on the client. Logged as a known issue.
     */
    fun saveUsername() {
        val current = _state.value
        if (!current.canSaveUsername) return
        _state.update { it.copy(isSaving = true, error = null) }
        makeAWish<User>(PROFILE.SET_USERNAME, showLoader = false) { setUsername(current.draftUsername) }
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

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
        _state.update {
            it.copy(isLoading = false, isSaving = false, isCheckingUsername = false, error = error.displayMessage())
        }
    }

    override fun clearError() = _state.update { it.copy(error = null, message = null) }
}
