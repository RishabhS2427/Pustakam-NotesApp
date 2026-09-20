package com.app.pustakam.feature.notes.data.sync

import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.common.util.resolveLocalFilePath
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.filesys.path.PathPolicy
import com.app.pustakam.core.filesys.platform.FileReader
import com.app.pustakam.core.filesys.platform.FileWriter
import com.app.pustakam.core.filesys.platform.StoragePaths
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.sync.SyncConfig
import com.app.pustakam.core.network.ApiCallClient
import com.app.pustakam.core.network.MediaUpload
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "MediaSyncer"

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
/**
 * 🖼️ 20-Sep-2026 — public on purpose. This is the app's ONE file-transfer service: give it a note
 * and it moves whatever bytes are missing, in whichever direction they are missing. Nothing in here
 * knows about the sync cycle, watermarks or the dirty queue, so any other feature that holds
 * [NoteContentModel.MediaContent] rows (chat attachments, profile media, an export) can call these
 * two functions directly.
 */
class MediaSyncer : KoinComponent {

    private val apiClient: ApiCallClient by inject()
    private val fileReader: FileReader by inject()
    private val fileWriter: FileWriter by inject()
    private val storagePaths: StoragePaths by inject()

    /** [note] is unchanged (same instance) when nothing moved, so callers can skip the DB write.
     *  [reasons] carries WHY each file did not move, so the caller can log it under its own tag —
     *  a skip counter with the reason under a different tag is a number nobody can act on. */
    data class Outcome(
        val note: Note,
        val moved: Int,
        val skipped: Int,
        val reasons: List<String> = emptyList(),
    )

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
                ?: return@map content.also { reasons += "${content.id}: localPath did not resolve (${content.localPath})" }

            // 🖼️ the file sits outside app storage — read it where it actually is rather than
            //   giving up. A picked or shared file can legitimately live outside the sandbox, and
            //   refusing those meant "some of my attachments never sync" with no way to tell which.
            val relative = storagePaths.toRelative(absolute)

            val bytes = (if (relative != null) fileReader.read(relative) else fileReader.readAbsolute(absolute))
                ?: return@map content.also {
                    reasons += "${content.id}: unreadable at $absolute"
                }

            if (bytes.size > SyncConfig.MAX_MEDIA_BYTES) {
                reasons += "${content.id}: ${bytes.size} bytes over the ${SyncConfig.MAX_MEDIA_BYTES} cap"
                return@map content
            }

            val fileName = (relative ?: absolute).substringAfterLast('/')
            val upload = MediaUpload(
                contentId = content.id,
                fileName = fileName,
                mimeType = declaredMimeFor(content, fileName),
                bytes = bytes,
            )

            val asset = when (val result = apiClient.uploadMedia(upload)) {
                is Result.Success -> result.data.data?.files?.firstOrNull()
                else -> null
            }

            if (asset == null) {
                reasons += "${content.id}: the server did not accept the upload"
                return@map content
            }

            moved++
            // 🖼️ withAsset does NOT touch updatedAt — gaining a server identity is not a user edit
            content.withAsset(asset.assetId, asset.url, asset.checksum)
        }

        // copy(): stamping an assetId must not re-stamp updatedAt the way withContents() would
        return Outcome(
            note = if (moved > 0) note.copy(contents = contents) else note,
            moved = moved,
            skipped = reasons.size,
            reasons = reasons,
        )
    }

    /** 🖼️ eager, but [budget] bounded so a first sync on a large library cannot stall the cycle. */
    suspend fun downloadMissing(userId: String, note: Note, budget: Int): Outcome {
        if (note.deleted) return Outcome(note, 0, 0)
        var moved = 0
        var skipped = 0
        var remaining = budget

        val contents = note.contents.map { content ->
            if (content !is NoteContentModel.MediaContent || !content.needsDownload()) return@map content
            val assetId = content.assetId ?: return@map content

            if (remaining <= 0) {
                skipped++
                return@map content
            }
            remaining--

            val bytes = when (val result = apiClient.downloadMedia(userId, assetId)) {
                is Result.Success -> result.data
                else -> return@map content.also {
                    skipped++
                    log_d(TAG, "download failed for asset $assetId; retries next cycle")
                }
            }

            val destination = PathPolicy.importPath(note.id, fileNameFor(assetId, content))
            if (!fileWriter.write(destination.relativePath, bytes)) {
                skipped++
                log_d(TAG, "could not write ${destination.relativePath}")
                return@map content
            }

            moved++
            // 🖼️ ABSOLUTE — that is the form getMediaUrl()/resolveLocalFilePath and every
            //   playback call site expect. Storing the relative path here breaks rendering.
            content.withLocalPath(storagePaths.toAbsolute(destination.relativePath))
        }

        return Outcome(if (moved > 0) note.copy(contents = contents) else note, moved, skipped)
    }

    // 🖼️ the asset id IS unique, so no collision handling is needed; the extension keeps the
    //   existing type-detection-by-name path working (MimeCatalog.contentTypeFor).
    private fun fileNameFor(assetId: String, content: NoteContentModel.MediaContent): String {
        // 🎧 20-Sep-2026 — every audio type used to land as ".mp3", because extensionFor() maps the
        //   ContentType and ContentType.AUDIO.getExt() is ".mp3". An AAC recording written to a
        //   .mp3 name will not open on the receiving device. Audio keeps its real extension; every
        //   other type stays on the path it already used.
        audioExtensionFor(content.mimeType)?.let { return "$assetId$it" }
        val type = MimeCatalog.contentTypeForMime(content.mimeType) ?: content.type
        return "$assetId${MimeCatalog.extensionFor(type)}"
    }

    /**
     * 🎧 The server preserves a declared audio type over an MP4 container sniff — but it can only
     * preserve what it is told, and MimeCatalog.mimeFor(AUDIO) is "audio/mpeg" (MP3) for EVERY
     * recording, .m4a ones included. So the declaration has to come from the file itself.
     */
    private fun declaredMimeFor(content: NoteContentModel.MediaContent, fileName: String): String {
        val fallback = content.mimeType.trim().ifBlank { MimeCatalog.mimeFor(content.type) }
        if (content.type != ContentType.AUDIO) return fallback
        return audioMimeForExtension(fileName.substringAfterLast('.', "")) ?: fallback
    }

    private fun audioMimeForExtension(extension: String): String? =
        when (extension.lowercase()) {
            // 🎧 .mp4 included on purpose: Android's recorder writes AAC into an .mp4 name
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "amr" -> "audio/amr"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/x-flac"
            "mp3" -> "audio/mpeg"
            else -> null
        }

    private fun audioExtensionFor(mimeType: String): String? =
        when (mimeType.trim().lowercase().substringBefore(';')) {
            "audio/mp4", "audio/x-m4a" -> ".m4a"
            "audio/aac" -> ".aac"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/amr" -> ".amr"
            "audio/ogg" -> ".ogg"
            "audio/flac", "audio/x-flac" -> ".flac"
            "audio/mpeg" -> ".mp3"
            else -> null
        }
}
