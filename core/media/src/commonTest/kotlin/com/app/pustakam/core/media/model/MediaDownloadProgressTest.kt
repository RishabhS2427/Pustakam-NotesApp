package com.app.pustakam.core.media.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TEN_MB = 10L * 1024 * 1024

class MediaDownloadProgressTest {

    private fun downloading(bytes: Long, speed: Long = 0, total: Long = TEN_MB) =
        MediaDownloadProgress("a1", MediaDownloadState.DOWNLOADING, bytes, total, speed)

    @Test
    fun `percent is clamped, never divided by zero`() {
        assertEquals(0, downloading(0).percent)
        assertEquals(50, downloading(TEN_MB / 2).percent)
        assertEquals(100, downloading(TEN_MB).percent)
        assertEquals(100, downloading(TEN_MB * 2).percent)
        assertEquals(0, downloading(1024, total = 0).percent)
    }

    // 📥 a finished file reads 100% even when the server never declared a size
    @Test
    fun `a downloaded asset is always complete`() {
        assertEquals(100, MediaDownloadProgress.downloaded("a1").percent)
    }

    @Test
    fun `an unknown size makes the bar indeterminate rather than stuck at zero`() {
        assertTrue(downloading(4096, total = 0).isIndeterminate)
        assertFalse(downloading(4096).isIndeterminate)
        assertFalse(MediaDownloadProgress.notDownloaded("a1").isIndeterminate)
    }

    @Test
    fun `eta is unknown until there is a speed and a size`() {
        assertEquals(-1L, downloading(TEN_MB / 2, speed = 0).etaSeconds)
        assertEquals(-1L, downloading(TEN_MB / 2, speed = 1024, total = 0).etaSeconds)
        // ⏱️ 5 MB left at 1 MB/s
        assertEquals(5L, downloading(TEN_MB / 2, speed = 1024 * 1024).etaSeconds)
        assertEquals(-1L, MediaDownloadProgress.downloaded("a1", TEN_MB).etaSeconds)
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(0L, downloading(TEN_MB * 2).remainingBytes)
        assertEquals(TEN_MB / 2, downloading(TEN_MB / 2).remainingBytes)
    }

    @Test
    fun `the centre label says what is happening in every state`() {
        assertEquals("10.0 MB", MediaDownloadProgress.notDownloaded("a1", TEN_MB).centerLabel)
        assertEquals("Tap to download", MediaDownloadProgress.notDownloaded("a1", 0).centerLabel)
        assertEquals("Waiting…", downloading(0).copy(state = MediaDownloadState.QUEUED).centerLabel)
        assertEquals("50% · 5s left", downloading(TEN_MB / 2, speed = 1024 * 1024).centerLabel)
        assertEquals("50%", downloading(TEN_MB / 2).centerLabel)
        assertEquals("Paused · 50%", downloading(TEN_MB / 2).copy(state = MediaDownloadState.PAUSED).centerLabel)
        assertEquals("", MediaDownloadProgress.downloaded("a1", TEN_MB).centerLabel)
        assertEquals("Download failed · Tap to retry", downloading(0).copy(state = MediaDownloadState.FAILED).centerLabel)
        assertEquals(
            "Download failed · No signal",
            downloading(0).copy(state = MediaDownloadState.FAILED, failureReason = "No signal").centerLabel,
        )
    }

    // 📥 the end of the bar is downloaded / remaining, not downloaded / total
    @Test
    fun `the edge label is what landed and what is left`() {
        assertEquals("5.0 MB / 5.0 MB", downloading(TEN_MB / 2).edgeLabel)
        assertEquals("", MediaDownloadProgress.notDownloaded("a1", TEN_MB).edgeLabel)
        assertEquals("10.0 MB", MediaDownloadProgress.downloaded("a1", TEN_MB).edgeLabel)
        assertEquals("", downloading(4096, total = 0).edgeLabel)
    }

    @Test
    fun `state answers the three questions a card asks`() {
        assertTrue(MediaDownloadState.DOWNLOADING.isBusy())
        assertTrue(MediaDownloadState.PAUSED.isBusy())
        assertTrue(MediaDownloadState.QUEUED.isBusy())
        assertFalse(MediaDownloadState.NOT_DOWNLOADED.isBusy())
        assertFalse(MediaDownloadState.DOWNLOADED.isBusy())

        assertTrue(MediaDownloadState.PAUSED.canResume())
        assertTrue(MediaDownloadState.FAILED.canResume())
        assertFalse(MediaDownloadState.DOWNLOADING.canResume())

        assertTrue(MediaDownloadState.DOWNLOADED.isReady())
        assertFalse(MediaDownloadState.PAUSED.isReady())
    }
}
