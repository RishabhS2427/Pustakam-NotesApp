package com.app.pustakam.core.media.upload

import com.app.pustakam.core.media.model.MediaUploadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaUploadTrackerTest {

    @Test
    fun `an upload moves from uploading to failed and back`() {
        val tracker = MediaUploadTracker()
        tracker.started("c1")
        assertEquals(MediaUploadState.UPLOADING, tracker.statuses.value["c1"]?.state)

        tracker.failed("c1", "No signal")
        assertEquals(MediaUploadState.FAILED, tracker.statuses.value["c1"]?.state)
        assertEquals("No signal", tracker.statuses.value["c1"]?.failureReason)

        // ⬆️ a retry clears the old error the moment the new attempt starts
        tracker.started("c1")
        assertEquals(MediaUploadState.UPLOADING, tracker.statuses.value["c1"]?.state)
        assertNull(tracker.statuses.value["c1"]?.failureReason)
    }

    // ⬆️ success leaves nothing on the card, so the entry is removed rather than marked done
    @Test
    fun `a finished upload leaves no trace`() {
        val tracker = MediaUploadTracker()
        tracker.started("c1")
        tracker.finished("c1")
        assertNull(tracker.statuses.value["c1"])
    }

    @Test
    fun `progress is published per whole percent`() {
        val tracker = MediaUploadTracker()
        tracker.progress("c1", sent = 500, total = 1000)
        val first = tracker.statuses.value["c1"]
        assertEquals(50, first?.percent)
        tracker.progress("c1", sent = 504, total = 1000)
        assertEquals(first, tracker.statuses.value["c1"], "a sub-percent step must not republish")
        tracker.progress("c1", sent = 510, total = 1000)
        assertEquals(51, tracker.statuses.value["c1"]?.percent)
    }

    @Test
    fun `files are tracked independently`() {
        val tracker = MediaUploadTracker()
        tracker.failed("c1", "No signal")
        tracker.started("c2")
        assertEquals(MediaUploadState.FAILED, tracker.statuses.value["c1"]?.state)
        assertEquals(MediaUploadState.UPLOADING, tracker.statuses.value["c2"]?.state)
    }
}
