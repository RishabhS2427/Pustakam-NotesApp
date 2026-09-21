package com.app.pustakam.core.media.model

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEN_MB = 10L * 1024 * 1024

// 📥⬆️ which strip a card draws — the rule both platforms share, pinned case by case
class MediaTransferUiTest {

    private fun media(localPath: String? = null, assetId: String? = null) = NoteContentModel.MediaContent(
        position = 0.0,
        noteId = "note-1",
        type = ContentType.VIDEO,
        updatedAt = null,
        createdAt = null,
        id = "content-1",
        localPath = localPath,
        assetId = assetId,
        sizeBytes = TEN_MB,
    )

    @Test
    fun `a file on the server but not here offers a download`() {
        val ui = MediaTransferUi.of(media(assetId = "a1"), download = null, upload = null)
        assertEquals(MediaButtonAction.DOWNLOAD, ui?.buttonAction)
        assertEquals("10.0 MB", ui?.centerLabel)
        assertFalse(ui!!.isError)
    }

    @Test
    fun `a file already here draws nothing`() {
        assertNull(MediaTransferUi.of(media(localPath = "/files/v.mp4", assetId = "a1"), null, null))
    }

    // 📥 no asset id yet: the other device is still uploading — wait visibly, no button (the app checks itself)
    @Test
    fun `a block whose file has not reached the server yet waits without a button`() {
        val ui = MediaTransferUi.of(media(), null, null)!!
        assertEquals("Waiting for the file…", ui.centerLabel)
        assertEquals(MediaButtonAction.NONE, ui.buttonAction)
        assertTrue(ui.showsBar)
        assertTrue(ui.isIndeterminate)
        assertFalse(ui.isError)
    }

    @Test
    fun `a finished download draws nothing`() {
        val done = MediaDownloadProgress.downloaded("a1", TEN_MB)
        assertNull(MediaTransferUi.of(media(assetId = "a1"), done, null))
    }

    // ❗ the user asked to SEE a failed download as an error, with a way to retry it
    @Test
    fun `a failed download is an error with a retry`() {
        val failed = MediaDownloadProgress("a1", MediaDownloadState.FAILED, 1024, TEN_MB, failureReason = "No signal")
        val ui = MediaTransferUi.of(media(assetId = "a1"), failed, null)!!
        assertTrue(ui.isError)
        assertEquals("Download failed · No signal", ui.centerLabel)
        assertEquals(MediaButtonAction.RETRY, ui.buttonAction)
        assertFalse(ui.canCancel)
    }

    @Test
    fun `a running download shows the bar and can be cancelled`() {
        val running = MediaDownloadProgress("a1", MediaDownloadState.DOWNLOADING, TEN_MB / 2, TEN_MB)
        val ui = MediaTransferUi.of(media(assetId = "a1"), running, null)!!
        assertTrue(ui.showsBar)
        assertTrue(ui.canCancel)
        assertEquals(0.5f, ui.fraction)
        assertEquals(MediaButtonAction.PAUSE, ui.buttonAction)
    }

    // ⬆️ the sender's side: its file is local, so only the upload can have something to say
    @Test
    fun `an upload in flight shows a busy bar and no button`() {
        val uploading = MediaUploadStatus("content-1", MediaUploadState.UPLOADING)
        val ui = MediaTransferUi.of(media(localPath = "/files/v.mp4"), null, uploading)!!
        assertEquals("Uploading…", ui.centerLabel)
        assertEquals("10.0 MB", ui.edgeLabel)
        assertTrue(ui.showsBar)
        assertTrue(ui.isIndeterminate)
        assertEquals(MediaButtonAction.NONE, ui.buttonAction)
        assertFalse(ui.isError)
    }

    // ❗ the user asked to SEE a failed upload — it used to exist only in the log
    @Test
    fun `a failed upload is an error with a retry`() {
        val failed = MediaUploadStatus("content-1", MediaUploadState.FAILED, "That file is too large to upload.")
        val ui = MediaTransferUi.of(media(localPath = "/files/v.mp4"), null, failed)!!
        assertTrue(ui.isError)
        assertEquals("Upload failed · That file is too large to upload.", ui.centerLabel)
        assertEquals(MediaButtonAction.RETRY, ui.buttonAction)
        assertFalse(ui.showsBar)
    }

    // ⬆️ the upload bar moves with the bytes the network layer reports
    @Test
    fun `an upload with reported bytes shows a real bar`() {
        val halfway = MediaUploadStatus("content-1", MediaUploadState.UPLOADING, bytesSent = TEN_MB / 2, totalBytes = TEN_MB)
        val ui = MediaTransferUi.of(media(localPath = "/files/v.mp4"), null, halfway)!!
        assertEquals("Uploading 50%", ui.centerLabel)
        assertEquals("5.0 MB / 5.0 MB", ui.edgeLabel)
        assertEquals(0.5f, ui.fraction)
        assertFalse(ui.isIndeterminate)
    }

    // 📥 the path says "here" but the file is gone — offer the download again
    @Test
    fun `a missing local file offers to download it again`() {
        val ui = MediaTransferUi.of(media(localPath = "/files/gone.mp4", assetId = "a1"), null, null, localFileMissing = true)!!
        assertTrue(ui.isError)
        assertEquals("File not found · Tap to download again", ui.centerLabel)
        assertEquals(MediaButtonAction.RETRY, ui.buttonAction)
    }

    @Test
    fun `a missing file being fetched again shows its progress`() {
        val running = MediaDownloadProgress("a1", MediaDownloadState.DOWNLOADING, TEN_MB / 2, TEN_MB)
        val ui = MediaTransferUi.of(media(localPath = "/files/gone.mp4", assetId = "a1"), running, null, localFileMissing = true)!!
        assertEquals(MediaButtonAction.PAUSE, ui.buttonAction)
        assertTrue(ui.showsBar)
    }

    @Test
    fun `a local file already uploaded draws nothing`() {
        assertNull(MediaTransferUi.of(media(localPath = "/files/v.mp4", assetId = "a1"), null, null))
    }

    // ⬆️ queued behind other uploads — the sender sees it is waiting, not nothing
    @Test
    fun `a local file not yet uploaded says it is waiting`() {
        val ui = MediaTransferUi.of(media(localPath = "/files/v.mp4"), null, null)!!
        assertEquals("Waiting to upload · Tap to start", ui.centerLabel)
        assertEquals(MediaButtonAction.RETRY, ui.buttonAction)
        assertFalse(ui.isError)
    }
}
