package com.app.pustakam.core.media.model

import com.app.pustakam.core.media.format.MediaSizeFormatter

private const val PERCENT_MAX = 100
private const val UNKNOWN_ETA = -1L

// 📥 one attachment's transfer as the card sees it — every label is computed here for both platforms
data class MediaDownloadProgress(
    val assetId: String,
    val state: MediaDownloadState = MediaDownloadState.NOT_DOWNLOADED,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val failureReason: String? = null,
) {

    // 📥 0..100; a download whose size the server never declared stays at 0 and the bar goes busy
    val percent: Int
        get() = when {
            state == MediaDownloadState.DOWNLOADED -> PERCENT_MAX
            totalBytes <= 0 -> 0
            else -> ((bytesDownloaded * PERCENT_MAX) / totalBytes).toInt().coerceIn(0, PERCENT_MAX)
        }

    // 📥 0f..1f for the bar itself
    val fraction: Float get() = percent / PERCENT_MAX.toFloat()

    // 📥 true when the size is unknown, so the bar must spin instead of fill
    val isIndeterminate: Boolean
        get() = state == MediaDownloadState.DOWNLOADING && totalBytes <= 0

    val remainingBytes: Long get() = (totalBytes - bytesDownloaded).coerceAtLeast(0)

    // ⏱️ seconds left at the current speed, or -1 while that cannot be known
    val etaSeconds: Long
        get() = when {
            state != MediaDownloadState.DOWNLOADING -> UNKNOWN_ETA
            bytesPerSecond <= 0 || totalBytes <= 0 -> UNKNOWN_ETA
            else -> remainingBytes / bytesPerSecond
        }

    // ⏯️ what the centre button does here — both platforms only map this to an icon
    val buttonAction: MediaButtonAction
        get() = when (state) {
            MediaDownloadState.NOT_DOWNLOADED -> MediaButtonAction.DOWNLOAD
            MediaDownloadState.QUEUED, MediaDownloadState.DOWNLOADING -> MediaButtonAction.PAUSE
            MediaDownloadState.PAUSED -> MediaButtonAction.RESUME
            MediaDownloadState.FAILED -> MediaButtonAction.RETRY
            MediaDownloadState.DOWNLOADED -> MediaButtonAction.NONE
        }

    // 📥 centre of the bar: percentage, then the ETA once the speed has settled
    val centerLabel: String
        get() = when (state) {
            // 📥 older notes carry size 0 — "0 B" reads like an empty file, so say what to do instead
            MediaDownloadState.NOT_DOWNLOADED ->
                if (totalBytes > 0) MediaSizeFormatter.formatBytes(totalBytes) else "Tap to download"
            MediaDownloadState.QUEUED -> "Waiting…"
            MediaDownloadState.DOWNLOADING -> downloadingLabel()
            MediaDownloadState.PAUSED -> "Paused · ${MediaSizeFormatter.formatPercent(percent)}"
            MediaDownloadState.DOWNLOADED -> ""
            // ❗ 21-Sep-2026 — says it FAILED, then why; the reason alone read like a status, not an error
            MediaDownloadState.FAILED -> "Download failed · ${failureReason ?: "Tap to retry"}"
        }

    // 📥 end of the bar: what has landed and what is still to come
    val edgeLabel: String
        get() = when {
            state == MediaDownloadState.DOWNLOADED -> MediaSizeFormatter.formatBytes(totalBytes)
            !state.isBusy() || totalBytes <= 0 -> ""
            else -> "${MediaSizeFormatter.formatBytes(bytesDownloaded)} / " +
                MediaSizeFormatter.formatBytes(remainingBytes)
        }

    // 📥 the strip this progress draws; upload status has its own toUi() with the same shape
    fun toUi(): MediaTransferUi = MediaTransferUi(
        centerLabel = centerLabel,
        edgeLabel = edgeLabel,
        fraction = fraction,
        showsBar = state.isBusy(),
        isIndeterminate = isIndeterminate,
        isError = state == MediaDownloadState.FAILED,
        canCancel = state.isBusy(),
        buttonAction = buttonAction,
    )

    private fun downloadingLabel(): String {
        val percentText = MediaSizeFormatter.formatPercent(percent)
        val eta = MediaSizeFormatter.formatEta(etaSeconds)
        return if (eta.isEmpty()) percentText else "$percentText · $eta left"
    }

    companion object {
        // 📥 an asset nobody has asked for yet; totalBytes comes from the note's own sizeBytes
        fun notDownloaded(assetId: String, totalBytes: Long = 0): MediaDownloadProgress =
            MediaDownloadProgress(assetId, MediaDownloadState.NOT_DOWNLOADED, 0, totalBytes)

        // 📥 already on disk — what a card shows for every file that synced eagerly
        fun downloaded(assetId: String, totalBytes: Long = 0): MediaDownloadProgress =
            MediaDownloadProgress(assetId, MediaDownloadState.DOWNLOADED, totalBytes, totalBytes)
    }
}
