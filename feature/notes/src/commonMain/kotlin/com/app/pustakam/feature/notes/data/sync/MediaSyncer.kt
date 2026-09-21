package com.app.pustakam.feature.notes.data.sync

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.NetworkError
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.resolveLocalFilePath
import com.app.pustakam.core.media.naming.MediaFileNaming
import com.app.pustakam.core.media.upload.MediaUploadTracker
import com.app.pustakam.core.filesys.platform.DirectoryManager
import com.app.pustakam.core.filesys.platform.FileReader
import com.app.pustakam.core.filesys.platform.StoragePaths
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.network.ApiCallClient
import com.app.pustakam.core.network.MediaUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// ⬆️ what the sender's card says when a file cannot go up — the same words on both platforms
private const val FILE_MISSING = "The file is missing on this device."
private const val FILE_UNREADABLE = "The file could not be read. It may be too large for this device."
private const val SERVER_REFUSED = "The server did not accept the file."

/**
 * 🖼️ 20-Aug-2026 — the bytes half of sync.
 *
 * A media block travels as `assetId` + `checksum` + `mimeType`/`sizeBytes`. `localPath` is this
 * device's own copy and never leaves it: the server strips it (backend.md S1-11/E22) and
 * [NoteWireMapper] does not send it.
 *
 * Nothing here throws. A file that cannot move is SKIPPED and counted, because one oversized video
 * must not stop a hundred notes from syncing.
 */
// 🖼️ the UPLOAD half, reusable by any feature — downloads live in :core:media's MediaDownloadManager
class MediaSyncer : KoinComponent {

    private val apiClient: ApiCallClient by inject()
    private val fileReader: FileReader by inject()
    private val storagePaths: StoragePaths by inject()
    private val directories: DirectoryManager by inject()

    // ⬆️ 21-Sep-2026 — what the sender's card shows; a failed upload used to exist only in the log
    private val uploads: MediaUploadTracker by inject()

    /** [note] is unchanged (same instance) when nothing moved, so callers can skip the DB write.
     *  [reasons] carries WHY each file did not move, so the caller can log it under its own tag —
     *  a skip counter with the reason under a different tag is a number nobody can act on. */
    data class Outcome(
        val note: Note,
        val moved: Int,
        val skipped: Int,
        val reasons: List<String> = emptyList(),
    )

    // ⬆️ 21-Sep-2026 — bytes still waiting to go up for this note, read from disk (recordings carry size 0)
    fun pendingBytes(note: Note): Long = note.contents
        .filterIsInstance<NoteContentModel.MediaContent>()
        .filter { it.needsUpload() }
        .sumOf { content ->
            val relative = resolveLocalFilePath(content.localPath)?.let { storagePaths.toRelative(it) }
            if (relative != null) directories.sizeOf(relative) else content.sizeBytes
        }

    /** 🖼️ runs BEFORE the note is pushed, so a note never references an asset the server lacks. */
    suspend fun uploadPending(note: Note, budget: Int = Int.MAX_VALUE): Outcome {
        if (note.deleted) return Outcome(note, 0, 0)
        var moved = 0
        val reasons = mutableListOf<String>()
        var remaining = budget

        val contents = note.contents.map { content ->
            if (content !is NoteContentModel.MediaContent || !content.needsUpload()) return@map content

            // 🖼️ out of budget for this cycle — still pending, picked up by the next run
            if (remaining <= 0) {
                reasons += "${content.id}: over the per-cycle budget"
                return@map content
            }
            remaining--

            val absolute = resolveLocalFilePath(content.localPath)
                ?: return@map content.also {
                    reasons += "${content.id}: localPath did not resolve (${content.localPath})"
                    uploads.failed(content.id, FILE_MISSING)
                }

            // 🖼️ the file sits outside app storage — read it where it actually is rather than
            //   giving up. A picked or shared file can legitimately live outside the sandbox, and
            //   refusing those meant "some of my attachments never sync" with no way to tell which.
            val relative = storagePaths.toRelative(absolute)

            // ⬆️ 21-Sep-2026 — a deleted file says so, rather than being reported as unreadable
            if (relative != null && !directories.exists(relative)) {
                reasons += "${content.id}: missing at $absolute"
                uploads.failed(content.id, FILE_MISSING)
                return@map content
            }

            // ⬆️ 21-Sep-2026 — no size cap now: a file too big for memory fails visibly instead of crashing (OOM is an Error)
            val bytes = try {
                withContext(Dispatchers.IO) {
                    if (relative != null) fileReader.read(relative) else fileReader.readAbsolute(absolute)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            } ?: return@map content.also {
                reasons += "${content.id}: unreadable at $absolute"
                uploads.failed(content.id, FILE_UNREADABLE)
            }

            val fileName = (relative ?: absolute).substringAfterLast('/')
            val upload = MediaUpload(
                contentId = content.id,
                fileName = fileName,
                mimeType = MediaFileNaming.declaredMimeFor(content.type, content.mimeType, fileName),
                bytes = bytes,
            )

            // 🖼️ 21-Sep-2026 — keep WHY: the log gets the error's name, the card gets words a person can act on
            uploads.started(content.id)
            var refusal: Error? = null
            val asset = when (val result = apiClient.uploadMedia(upload) { sent, total -> uploads.progress(content.id, sent, total) }) {
                is Result.Success -> result.data.data?.files?.firstOrNull()
                is Result.Error -> null.also { refusal = result.error }
                else -> null
            }

            if (asset == null) {
                reasons += "${content.id}: the server did not accept the upload (${refusal ?: "no file in the reply"})"
                uploads.failed(content.id, (refusal as? NetworkError)?.getError() ?: SERVER_REFUSED)
                return@map content
            }

            uploads.finished(content.id)
            moved++
            // 🖼️ withAsset does NOT touch updatedAt — gaining a server identity is not a user edit
            // 📥 21-Sep-2026 — recordings are saved with size 0 and no type; take the server's so the receiver can size, verify and name it
            content.withAsset(asset.assetId, asset.url, asset.checksum).copy(
                mimeType = content.mimeType.ifBlank { asset.mimeType },
                sizeBytes = if (content.sizeBytes > 0) content.sizeBytes else asset.sizeBytes,
            )
        }

        // copy(): stamping an assetId must not re-stamp updatedAt the way withContents() would
        return Outcome(
            note = if (moved > 0) note.copy(contents = contents) else note,
            moved = moved,
            skipped = reasons.size,
            reasons = reasons,
        )
    }
}
