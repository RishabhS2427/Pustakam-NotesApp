package com.app.pustakam.core.media.upload

import com.app.pustakam.core.media.model.MediaUploadState
import com.app.pustakam.core.media.model.MediaUploadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ⬆️ the upload half writes here, every card reads it — in memory; the next sync attempt re-reports
class MediaUploadTracker {

    private val _statuses = MutableStateFlow<Map<String, MediaUploadStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, MediaUploadStatus>> = _statuses.asStateFlow()

    fun started(contentId: String) = set(MediaUploadStatus(contentId, MediaUploadState.UPLOADING))

    // ⬆️ only a whole-percent change is published — the network reports far faster than a card can redraw
    fun progress(contentId: String, sent: Long, total: Long) {
        val next = MediaUploadStatus(contentId, MediaUploadState.UPLOADING, bytesSent = sent, totalBytes = total)
        _statuses.update { all ->
            val current = all[contentId]
            if (current?.state == MediaUploadState.UPLOADING && current.totalBytes == total && current.percent == next.percent) all
            else all + (contentId to next)
        }
    }

    fun failed(contentId: String, reason: String) =
        set(MediaUploadStatus(contentId, MediaUploadState.FAILED, reason))

    // ⬆️ success leaves nothing to show, so the entry goes rather than turning into "done"
    fun finished(contentId: String) = _statuses.update { it - contentId }

    private fun set(status: MediaUploadStatus) = _statuses.update { it + (status.contentId to status) }
}
