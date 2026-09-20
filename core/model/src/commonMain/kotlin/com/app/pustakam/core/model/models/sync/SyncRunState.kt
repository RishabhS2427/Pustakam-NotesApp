package com.app.pustakam.core.model.models.sync

// 🔄 20-Aug-2026 sync: what the UI shows. Named SyncRunState because SQLDelight generates a
//   SyncState row class from the SyncState table — two different things, deliberately different names.
sealed class SyncRunState {

    data object Idle : SyncRunState()

    // 🔄 offline is a resting state, not a failure — nothing is wrong and nothing needs retrying
    data object Offline : SyncRunState()

    data object Syncing : SyncRunState()

    data class Success(val at: Long, val pushed: Int, val pulled: Int) : SyncRunState()

    data class Failed(val message: String, val retryInMillis: Long) : SyncRunState()

    fun isBusy(): Boolean = this is Syncing
}

data class SyncSummary(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val mediaUploaded: Int = 0,
    val mediaDownloaded: Int = 0,
    // 🔄 29-Aug-2026 — a cycle that "succeeded" while the server refused notes, or while files could
    //   not move, is exactly what silent data loss looks like. Count both.
    val rejected: Int = 0,
    val mediaSkipped: Int = 0,
)

// 🔄 tuned so one push cannot exceed the server's SYNC_MAX_NOTES_PER_PUSH, and one pull page stays
//   under the server's max limit of 500
object SyncConfig {
    const val PUSH_BATCH = 50
    const val PULL_LIMIT = 200
    const val MAX_PULL_PAGES = 100

    // 🔄 28-Aug-2026 — a pull stops rather than skip a note holding an unpushed local edit. After
    //   this many cycles the push has provably failed on it, and the server copy is taken instead:
    //   one unsendable note must not block every incoming change on this device.
    const val MAX_STALLED_CYCLES = 3

    // 🔄 28-Aug-2026 — bump this whenever a fix changes what a stored watermark MEANS. Every device
    //   still on an older generation re-pulls from the beginning once, then records the new one.
    //   Generation 1 exists because the first build advanced the watermark past notes it declined
    //   to write, so those notes could never be delivered again.
    const val RESYNC_GENERATION = 1
    const val INTERVAL_MILLIS = 15L * 60L * 1000L

    // 🔄 29-Aug-2026 — there is no server push channel, so a device only learns about another
    //   device's edit when it asks. While the app is ON SCREEN it asks this often; 15 minutes is
    //   for the background. Raise the server's RateLimitMax.SYNC together with this.
    const val FOREGROUND_INTERVAL_MILLIS = 20L * 1000L
    // 🔄 28-Aug-2026 — short enough to feel immediate, long enough that typing is one push
    const val SAVE_DEBOUNCE_MILLIS = 1200L

    // 🔄 28-Aug-2026 — how long a sync waits for preferences to hydrate before calling the session dead
    const val SESSION_WAIT_MILLIS = 3000L

    // 🔄 29-Aug-2026 — backoff is for a device nobody is looking at. On screen it must never grow
    //   past this, or one failed cycle silently turns the 20s poll into a 30-minute one.
    const val FOREGROUND_MAX_BACKOFF_MILLIS = 60L * 1000L
    const val BASE_BACKOFF_MILLIS = 30L * 1000L
    const val MAX_BACKOFF_MILLIS = 30L * 60L * 1000L

    // 🖼️ matches the server's UPLOAD_MAX_BYTES default. A bigger file is SKIPPED and logged, never
    //   allowed to fail the whole cycle — raise UPLOAD_MAX_BYTES on the server and this together.
    // 🖼️ 20-Sep-2026 — was 25 MB, which silently dropped ordinary note attachments: a scanned PDF
    //   or a few minutes of audio clears that easily, and the file then never synced at all.
    const val MAX_MEDIA_BYTES = 100 * 1024 * 1024

    // 🖼️ eager download, but bounded per cycle so a first sync on a big library does not stall
    const val MEDIA_FILES_PER_CYCLE = 100
    const val MEDIA_NOTES_PER_CYCLE = 200
}
