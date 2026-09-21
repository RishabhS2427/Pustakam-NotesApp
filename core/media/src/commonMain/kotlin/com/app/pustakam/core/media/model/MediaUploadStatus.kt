package com.app.pustakam.core.media.model

import com.app.pustakam.core.media.format.MediaSizeFormatter

// ⬆️ where one file's upload stands on the SENDING device; no entry means nothing to report
enum class MediaUploadState { UPLOADING, FAILED }

data class MediaUploadStatus(
    // ⬆️ the content block's id — an upload has no asset id until it succeeds
    val contentId: String,
    val state: MediaUploadState,
    val failureReason: String? = null,
    val bytesSent: Long = 0,
    val totalBytes: Long = 0,
) {
    // ⬆️ 0..100, from what the network layer reports as actually sent
    val percent: Int
        get() = if (totalBytes <= 0) 0 else ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)

    // ⬆️ the strip the sender's card shows, worded once for both platforms
    fun toUi(fileBytes: Long): MediaTransferUi = when (state) {
        // ⬆️ a real bar once the first bytes are reported; until then it just says it is starting
        MediaUploadState.UPLOADING -> MediaTransferUi(
            centerLabel = if (totalBytes > 0) "Uploading ${MediaSizeFormatter.formatPercent(percent)}" else "Uploading…",
            edgeLabel = when {
                totalBytes > 0 -> "${MediaSizeFormatter.formatBytes(bytesSent)} / " +
                    MediaSizeFormatter.formatBytes((totalBytes - bytesSent).coerceAtLeast(0))
                fileBytes > 0 -> MediaSizeFormatter.formatBytes(fileBytes)
                else -> ""
            },
            fraction = percent / 100f,
            showsBar = true,
            isIndeterminate = totalBytes <= 0,
            isError = false,
            canCancel = false,
            buttonAction = MediaButtonAction.NONE,
        )
        MediaUploadState.FAILED -> MediaTransferUi(
            centerLabel = "Upload failed · ${failureReason ?: "Tap to retry"}",
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
