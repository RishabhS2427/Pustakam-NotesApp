package com.app.pustakam.core.database.localdb.preferences

import kotlinx.coroutines.flow.Flow

interface IAppPreferences {
    val userPreferencesFlow: Flow<UserPreference>
    val readingModeFlow: Flow<String>

    suspend fun setToken(token : String)
    // 🔐 20-Aug-2026 sync: the rotating refresh token — access tokens live 15 minutes, sync outlives that
    suspend fun setRefreshToken(token : String)
    suspend fun getRefreshToken() : String?
    // 🔐 20-Aug-2026 sync: drop dead tokens WITHOUT clear() — clear() also wipes theme/reading prefs
    suspend fun clearTokens()
    suspend fun getAuthToken() : String?
    suspend  fun setUserId(userId : String)
    suspend  fun setAuth(isAuth : Boolean)
    // 🎨 22-Jul-2026 — Granth spec §6 Appearance: persisted theme mode (system/light/dark/amoled)
    suspend fun setThemeMode(mode : String)
    // 📖 23-Jul-2026 — reader: "page" (curl) vs "scroll" (continuous).
    //   Per-book resume is NOT here — it lives on MediaContent (progressPage/totalPages).
    suspend fun setReadingMode(mode : String)
    suspend fun  clear()
    // 🔄 28-Aug-2026 — which generation of the sync engine last wrote this device's pull watermark.
    //   Bumping SyncConfig.RESYNC_GENERATION makes every existing install re-pull once, which is
    //   how a device recovers from a watermark an older build advanced past a note it never wrote.
    suspend fun getSyncGeneration(): Int
    suspend fun setSyncGeneration(generation: Int)
    fun currentTokenOrNull(): String?

    // 🔒 logged-in user, read synchronously so a DAO query can scope itself
    fun currentUserId(): String
}
