package com.app.pustakam.core.media.model

import com.app.pustakam.core.model.models.response.notes.NoteContentModel

// 📥⬆️ what a card's strip shows, download OR upload — decided once in shared code for both platforms
data class MediaTransferUi(
    val centerLabel: String,
    val edgeLabel: String,
    val fraction: Float,
    val showsBar: Boolean,
    val isIndeterminate: Boolean,
    val isError: Boolean,
    val canCancel: Boolean,
    val buttonAction: MediaButtonAction,
) {
    companion object {
        // 📥⬆️ null = no strip; a local file can only be uploading/failed, a remote one only downloading/failed
        fun of(
            content: NoteContentModel.MediaContent,
            download: MediaDownloadProgress?,
            upload: MediaUploadStatus?,
            localFileMissing: Boolean = false,
        ): MediaTransferUi? {
            if (!content.localPath.isNullOrBlank() && !localFileMissing) {
                // ⬆️ 21-Sep-2026 — a local file with no asset id is queued to upload; saying nothing looked like "not syncing"
                return upload?.toUi(content.sizeBytes) ?: if (content.assetId.isNullOrBlank()) waitingToUpload() else null
            }
            // 📥 21-Sep-2026 — no asset id yet: the other device is still uploading it, so offer a refresh instead of nothing
            val assetId = content.assetId?.takeIf { it.isNotBlank() }
                ?: return upload?.toUi(content.sizeBytes) ?: waitingForFile()
            val progress = download ?: MediaDownloadProgress.notDownloaded(assetId, content.sizeBytes)
            // 📥 21-Sep-2026 — the path points at nothing: offer the download again unless one is already moving
            if (localFileMissing && !progress.state.isBusy() && progress.state != MediaDownloadState.FAILED) {
                return fileMissing()
            }
            return if (progress.state.isReady()) null else progress.toUi()
        }

        private fun waitingToUpload() = MediaTransferUi(
            centerLabel = "Waiting to upload · Tap to start",
            edgeLabel = "",
            fraction = 0f,
            showsBar = false,
            isIndeterminate = false,
            isError = false,
            canCancel = false,
            buttonAction = MediaButtonAction.RETRY,
        )

        // 📥 the other device is still uploading — the app checks by itself and downloads it, so no button
        private fun waitingForFile() = MediaTransferUi(
            centerLabel = "Waiting for the file…",
            edgeLabel = "",
            fraction = 0f,
            showsBar = true,
            isIndeterminate = true,
            isError = false,
            canCancel = false,
            buttonAction = MediaButtonAction.NONE,
        )

        private fun fileMissing() = MediaTransferUi(
            centerLabel = "File not found · Tap to download again",
            edgeLabel = "",
            fraction = 0f,
            showsBar = false,
            isIndeterminate = false,
            isError = true,
            canCancel = false,
            buttonAction = MediaButtonAction.RETRY,
        )
    }
}
