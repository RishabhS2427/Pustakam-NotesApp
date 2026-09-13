package com.app.pustakam.android.screen.settings

import androidx.lifecycle.viewModelScope
import com.app.pustakam.android.screen.PROFILE
import com.app.pustakam.android.screen.TaskCode
import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.android.screen.notebookReader.ReadingMode
import com.app.pustakam.android.theme.ThemeMode
import com.app.pustakam.core.database.localdb.preferences.IAppPreferences
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.core.model.models.response.displayName
import com.app.pustakam.core.model.models.response.handle
import com.app.pustakam.core.model.models.response.initial
import com.app.pustakam.feature.auth.domain.profile.GetMyProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.get
import org.koin.core.component.inject
import com.app.pustakam.core.common.util.Result

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val readingMode: ReadingMode = ReadingMode.PAGE,
    // Editor & Reading
    val editorFont: String = "Iowan",
    val editorFontSubtitle: String = "Display serif · 16pt",
    val markdownShortcuts: Boolean = true,
    val focusModeDimming: Boolean = true,
    val versionHistorySubtitle: String = "Keep 30 days of snapshots",
    // Sync & Backup
    val cloudSync: Boolean = true,
    val cloudSyncSubtitle: String = "Last synced 2 min ago · 1.2 GB",
    val autoBackup: Boolean = true,
    val offlineMode: Boolean = false,
    // More
    val language: String = "English",
    // 👤 31-Aug-2026 profile — read from the server, never hardcoded. Null until the fetch lands.
    val user: User? = null,
) {
    val profileInitial: String get() = user?.initial() ?: "?"

    val profileName: String get() = user?.displayName() ?: "Your profile"

    /**
     * 🔒 The handle, never the email. Email and phone are login credentials from 31-Aug-2026 and
     * the server no longer returns anyone else's; rendering our own here would still teach the
     * wrong habit, and the handle is what a person actually shares.
     */
    val profileSubtitle: String
        get() = when {
            user == null -> "Tap to set up"
            user.handle().isNotBlank() -> user.handle()
            else -> "Tap to pick a username"
        }

    /** 🆔 the nudge replaces the badge until a real handle exists. */
    val profileBadge: String? get() = if (user != null && user.username.isNullOrBlank()) "SET UP" else null
}

class SettingsViewModel : BaseViewModel() {

    private val userPrefs = get<IAppPreferences>()
    private val getMyProfile by inject<GetMyProfileUseCase>()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 🎨 restore the persisted Appearance pick so the tiles match the live theme on open
        viewModelScope.launch(Dispatchers.IO) {
            userPrefs.userPreferencesFlow.collect { pref ->
                _uiState.update { it.copy(themeMode = ThemeMode.from(pref.themeMode)) }
            }
        }
        // 📖 23-Jul-2026: same value the reader's toolbar toggle writes — the two stay in sync
        viewModelScope.launch(Dispatchers.IO) {
            userPrefs.readingModeFlow.collect { mode ->
                _uiState.update { it.copy(readingMode = ReadingMode.from(mode)) }
            }
        }
        loadProfile()
    }

    // 👤 no local cache by design — username must never be served stale (see ai/project-decisions.md)
    fun loadProfile() = makeAWish<User>(PROFILE.USER_PROFILE, showLoader = false) { getMyProfile() }

    // 📖 23-Jul-2026: flip reading mode from Settings
    fun onReadingModeChange(scrolling: Boolean) {
        val next = if (scrolling) ReadingMode.SCROLL else ReadingMode.PAGE
        _uiState.update { it.copy(readingMode = next) }
        viewModelScope.launch(Dispatchers.IO) { userPrefs.setReadingMode(next.key) }
    }

    // 🎨 persist the tile pick; the app root observes the same flow and retints immediately
    fun onThemeModeSelected(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch(Dispatchers.IO) { userPrefs.setThemeMode(mode.key) }
    }

    // 🎨 toggle rows — local state until each feature lands
    fun onMarkdownShortcutsChange(enabled: Boolean) =
        _uiState.update { it.copy(markdownShortcuts = enabled) }

    fun onFocusModeDimmingChange(enabled: Boolean) =
        _uiState.update { it.copy(focusModeDimming = enabled) }

    fun onCloudSyncChange(enabled: Boolean) =
        _uiState.update { it.copy(cloudSync = enabled) }

    fun onAutoBackupChange(enabled: Boolean) =
        _uiState.update { it.copy(autoBackup = enabled) }

    fun onOfflineModeChange(enabled: Boolean) =
        _uiState.update { it.copy(offlineMode = enabled) }

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {
        if (taskCode == PROFILE.USER_PROFILE) {
            _uiState.update { it.copy(user = result.data.data as? User) }
        }
    }


    override fun clearError() {}
}
