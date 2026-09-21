package com.app.pustakam.core.media.bridge

import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.media.download.MediaDownloadCoordinator
import com.app.pustakam.core.media.model.MediaTransferUi
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// 📥 the ONLY media-transfer entry point for iOS — same shape as SyncBridge: Main scope, Closeables, dispose()
class MediaDownloadBridge : KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val coordinator: MediaDownloadCoordinator by inject()

    // 📥⬆️ the strip for one card — the SAME stream Android collects, so both show the same thing
    fun observeTransfer(
        content: NoteContentModel.MediaContent,
        onEach: (MediaTransferUi?) -> Unit,
    ): Closeable {
        val job = scope.launch { coordinator.transferUi(content).collect { onEach(it) } }
        return Closeable(job)
    }

    fun currentTransferUi(content: NoteContentModel.MediaContent): MediaTransferUi? =
        coordinator.currentTransferUi(content)

    // ⏯️ the one action behind the button — pause, resume, download, or retry a failed upload
    fun toggle(content: NoteContentModel.MediaContent) = coordinator.toggle(content)

    fun cancel(content: NoteContentModel.MediaContent) = coordinator.cancel(content)

    fun dispose() {
        scope.cancel()
    }
}
