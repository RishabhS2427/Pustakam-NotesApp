package com.app.pustakam.feature.notes.domain.bridge

import com.app.pustakam.feature.notes.domain.usecase.UpdateReadingProgressUseCase
import com.app.pustakam.core.database.localdb.preferences.BasePreferences
import org.koin.core.component.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.data.bridge.watch

// 📖 23-Jul-2026: NEW — reader preferences for iOS, same pattern as AuthBridge (use-case/callback
//   style; Swift never sees suspend functions). Both platforms read and write the SAME DataStore
//   keys, so reading mode and per-book resume points behave identically on iOS and Android.
class ReaderPrefsBridge : KoinComponent {

    /** Observers: cancelled by dispose() when the screen dies. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        // 📖 writes must survive View-struct recreation / navigating away mid-flight
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    // 🔧 30-Jul-2026 02:10 was KoinHelper.getPreference() (:shared) — same Koin single, resolved directly; keeps :feature:notes below :shared
    private val prefs get() = get<BasePreferences>()

    /** Live reading mode — "page" (curl) or "scroll" (continuous). */
    fun observeReadingMode(onChange: (String) -> Unit): Closeable =
        prefs.readingModeFlow.watch(scope) { onChange(it) }

    fun setReadingMode(mode: String) {
        writeScope.launch { prefs.setReadingMode(mode) }
    }
    fun observeOfflineMode(onChange: (Boolean) -> Unit): Closeable =
        prefs.offlineModeFlow.watch(scope) { onChange(it) }
    fun setOfflineMode(mode: Boolean) {
        writeScope.launch { prefs.setOfflineMode(mode) }
    }


    // 📖 23-Jul-2026: reading progress lives on the document's media row. The bridge goes through the
    //   SAME use case Android uses (UpdateReadingProgressUseCase) — never the DAO directly — so the
    //   platform side only ever sees a use case.
    private val updateReadingProgressUseCase: UpdateReadingProgressUseCase by inject()

    /**
     * Persist where the reader stopped for ONE document.
     * [contentId] is the MediaContent's id — progress lives on that row, not in preferences.
     */
    fun saveReadingProgress(contentId: String, page: Int, totalPages: Int) {
        if (contentId.isEmpty()) return
        writeScope.launch {
            updateReadingProgressUseCase(contentId, page, totalPages).collect { }
        }
    }

    fun dispose() = scope.cancel()
}
