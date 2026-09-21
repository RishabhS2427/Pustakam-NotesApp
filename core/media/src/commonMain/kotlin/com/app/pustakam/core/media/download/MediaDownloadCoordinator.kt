package com.app.pustakam.core.media.download

import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.common.util.resolveLocalFilePath
import com.app.pustakam.core.database.localdb.preferences.IAppPreferences
import com.app.pustakam.core.filesys.platform.DirectoryManager
import com.app.pustakam.core.filesys.platform.StoragePaths
import com.app.pustakam.core.media.model.MediaDownloadProgress
import com.app.pustakam.core.media.model.MediaDownloadRequest
import com.app.pustakam.core.media.model.MediaDownloadState
import com.app.pustakam.core.media.model.MediaTransferUi
import com.app.pustakam.core.media.model.MediaUploadState
import com.app.pustakam.core.media.naming.MediaFileNaming
import com.app.pustakam.core.media.upload.MediaUploadRetry
import com.app.pustakam.core.media.upload.MediaUploadTracker
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// 📥⬆️ the one thing a card talks to — what is happening to these bytes (either way), and what the button does
// 🔄 how often a waiting card asks the server again — 10s keeps well under the sync rate limit
private const val WAITING_CHECK_MILLIS = 10_000L

class MediaDownloadCoordinator(
    private val manager: MediaDownloadManager,
    private val prefs: IAppPreferences,
    private val uploads: MediaUploadTracker,
    private val uploadRetry: MediaUploadRetry?,
    private val directories: DirectoryManager,
    private val storagePaths: StoragePaths,
) {

    val progress: StateFlow<Map<String, MediaDownloadProgress>> get() = manager.progress

    private var lastWaitingCheckAt = 0L

    // 📥⬆️ one card's strip as a stream — both platforms collect exactly this, so they cannot disagree
    fun transferUi(content: NoteContentModel.MediaContent): Flow<MediaTransferUi?> {
        val assetId = content.assetId.orEmpty()
        val missing = isLocalFileMissing(content)
        val ui = combine(manager.progress, uploads.statuses) { downloads, statuses ->
            MediaTransferUi.of(content, downloads[assetId], statuses[content.id], missing)
        }.distinctUntilChanged()
        val neverDownloaded = content.localPath.isNullOrBlank()
        return channelFlow {
            // 📥 21-Sep-2026 — on screen = downloading: no tap needed (paused, cancelled or failed stay as they are)
            if (neverDownloaded && assetId.isNotBlank()) startAutomatically(content)
            // 🔄 no asset id yet — keep checking while the card waits, so the download starts soon after the upload ends
            if (neverDownloaded && assetId.isBlank()) launch {
                while (true) {
                    delay(WAITING_CHECK_MILLIS)
                    checkForUploadedFile()
                }
            }
            ui.collect { send(it) }
        }
    }

    // 🔄 one sync at most every WAITING_CHECK_MILLIS however many cards are waiting — the server rate-limits sync
    private fun checkForUploadedFile() {
        val now = getCurrentTimestamp()
        if (now - lastWaitingCheckAt < WAITING_CHECK_MILLIS) return
        lastWaitingCheckAt = now
        uploadRetry?.retry()
    }

    // 📥⬆️ the same answer right now, so a card's first frame matches what the stream will say
    fun currentTransferUi(content: NoteContentModel.MediaContent): MediaTransferUi? =
        MediaTransferUi.of(
            content,
            manager.progress.value[content.assetId.orEmpty()],
            uploads.statuses.value[content.id],
            isLocalFileMissing(content),
        )

    // 📥 21-Sep-2026 — a stored path whose file is gone (deleted, or never landed); outside app storage is not checked
    fun isLocalFileMissing(content: NoteContentModel.MediaContent): Boolean {
        val path = content.localPath?.takeIf { it.isNotBlank() } ?: return false
        val absolute = resolveLocalFilePath(path) ?: return true
        val relative = storagePaths.toRelative(absolute) ?: return false
        return !directories.exists(relative)
    }

    // 📥 localPath = here; no assetId = can never be fetched, which is NOT "downloaded"
    fun stateOf(content: NoteContentModel.MediaContent): MediaDownloadProgress {
        val assetId = content.assetId
        if (!content.localPath.isNullOrBlank()) {
            return MediaDownloadProgress.downloaded(assetId ?: content.id, content.sizeBytes)
        }
        if (assetId.isNullOrBlank()) {
            return MediaDownloadProgress.notDownloaded(content.id, content.sizeBytes)
        }
        return manager.stateOf(assetId, content.sizeBytes)
    }

    // ⏯️ the ONE action behind the centre button, so a card never has to reason about states
    fun toggle(content: NoteContentModel.MediaContent) {
        // ⬆️ a failed upload retries through sync, the only place uploads run
        if (uploads.statuses.value[content.id]?.state == MediaUploadState.FAILED) {
            uploadRetry?.retry()
            return
        }
        // 🔄 no asset id yet — refresh is a sync, which pulls the id once the other device has uploaded
        val assetId = content.assetId?.takeIf { it.isNotBlank() } ?: run { uploadRetry?.retry(); return }
        // 📥 a missing file is judged by the transfer itself, not by the stale path that says "here"
        val missing = isLocalFileMissing(content)
        val state = if (missing) manager.stateOf(assetId).state else stateOf(content).state
        when (state) {
            MediaDownloadState.DOWNLOADING, MediaDownloadState.QUEUED -> manager.pause(assetId)
            MediaDownloadState.PAUSED -> manager.resume(assetId)
            MediaDownloadState.NOT_DOWNLOADED, MediaDownloadState.FAILED -> start(content)
            MediaDownloadState.DOWNLOADED -> if (missing) start(content)
        }
    }

    fun start(content: NoteContentModel.MediaContent) {
        requestFor(content)?.let { manager.enqueue(it) }
    }

    // 🔄 sync's entry point: fetch what is missing, never override a pause, never restart what is moving
    fun startAutomatically(content: NoteContentModel.MediaContent): Boolean {
        val request = requestFor(content) ?: return false
        val state = manager.stateOf(request.assetId).state
        // ❗ FAILED included: a failed download shows its error and waits for the person's tap to refresh
        if (state == MediaDownloadState.DOWNLOADING || state == MediaDownloadState.QUEUED ||
            state == MediaDownloadState.PAUSED || state == MediaDownloadState.FAILED
        ) return false
        manager.enqueue(request, automatic = true)
        return true
    }

    fun cancel(content: NoteContentModel.MediaContent) {
        content.assetId?.takeIf { it.isNotBlank() }?.let { manager.cancel(it) }
    }

    private fun requestFor(content: NoteContentModel.MediaContent): MediaDownloadRequest? {
        val assetId = content.assetId?.takeIf { it.isNotBlank() } ?: return null
        if (!content.localPath.isNullOrBlank() && !isLocalFileMissing(content)) return null
        val ownerId = prefs.currentUserId().takeIf { it.isNotBlank() } ?: return null
        return MediaDownloadRequest(
            assetId = assetId,
            ownerId = ownerId,
            holderId = content.noteId,
            destinationRelativePath = destinationFor(content, assetId),
            expectedBytes = content.sizeBytes,
        )
    }

    private fun destinationFor(content: NoteContentModel.MediaContent, assetId: String): String =
        MediaFileNaming.destinationFor(content.noteId, assetId, content.mimeType, content.type)
}
