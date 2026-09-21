package com.app.pustakam.core.media.download

import com.app.pustakam.core.common.util.NetworkError
import com.app.pustakam.core.media.model.MediaDownloadRequest
import com.app.pustakam.core.media.model.MediaDownloadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ASSET = "asset-1"
private const val OWNER = "user-1"
private const val HOLDER = "note-1"
private const val DESTINATION = "imported/note-1/asset-1.mp4"
private const val TOTAL = 8192L

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDownloadManagerTest {

    private fun request(expected: Long = TOTAL) = MediaDownloadRequest(
        assetId = ASSET,
        ownerId = OWNER,
        holderId = HOLDER,
        destinationRelativePath = DESTINATION,
        expectedBytes = expected,
    )

    private fun manager(
        scope: TestScope,
        source: MediaByteSource,
        store: MediaPartStore,
        maxParallel: Int = 2,
        handlers: List<MediaLandingHandler> = emptyList(),
    ) = MediaDownloadManager(
        source = source,
        store = store,
        scope = scope,
        // ⏱️ the virtual clock, so the emit throttle is deterministic
        clock = { scope.testScheduler.currentTime },
        maxParallel = maxParallel,
        handlers = handlers,
    )

    @Test
    fun `a finished download promotes the part file and reports 100 percent`() = runTest {
        val store = FakePartStore()
        val manager = manager(this, FakeByteSource(TOTAL), store)

        manager.enqueue(request())
        advanceUntilIdle()

        val progress = manager.stateOf(ASSET)
        assertEquals(MediaDownloadState.DOWNLOADED, progress.state)
        assertEquals(TOTAL, progress.bytesDownloaded)
        assertEquals(100, progress.percent)
        assertEquals(TOTAL.toInt(), store.files[DESTINATION]?.size)
        assertNull(store.files[DESTINATION + PART_SUFFIX], "the .part must not survive")
    }

    // ⏸️ the whole point of pausing: the bytes stay, and resume asks for the rest with a Range
    @Test
    fun `pause keeps the part file and resume continues from it`() = runTest {
        val store = FakePartStore()
        val source = FakeByteSource(TOTAL)
        source.gate = CompletableDeferred()
        val manager = manager(this, source, store)

        manager.enqueue(request())
        advanceUntilIdle()
        assertEquals(MediaDownloadState.DOWNLOADING, manager.stateOf(ASSET).state)

        manager.pause(ASSET)
        advanceUntilIdle()

        val paused = manager.stateOf(ASSET)
        assertEquals(MediaDownloadState.PAUSED, paused.state)
        assertEquals(0L, paused.bytesPerSecond, "a paused transfer has no speed")
        val kept = store.files[DESTINATION + PART_SUFFIX]?.size ?: 0
        assertTrue(kept > 0, "the partial bytes were thrown away")

        source.gate = null
        manager.resume(ASSET)
        advanceUntilIdle()

        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
        assertEquals(kept.toLong(), source.lastFromByte, "resume did not send the right offset")
        assertEquals(TOTAL.toInt(), store.files[DESTINATION]?.size)
    }

    @Test
    fun `cancel throws the partial bytes away and forgets the asset`() = runTest {
        val store = FakePartStore()
        val source = FakeByteSource(TOTAL)
        source.gate = CompletableDeferred()
        val manager = manager(this, source, store)

        manager.enqueue(request())
        advanceUntilIdle()
        manager.cancel(ASSET)
        advanceUntilIdle()

        assertNull(store.files[DESTINATION + PART_SUFFIX], "the .part leaked")
        assertEquals(MediaDownloadState.NOT_DOWNLOADED, manager.stateOf(ASSET).state)
    }

    @Test
    fun `a network failure is reported with its own message and keeps what landed`() = runTest {
        val store = FakePartStore()
        val manager = manager(this, FakeByteSource(TOTAL, failWith = NetworkError.NO_INTERNET), store)

        manager.enqueue(request())
        advanceUntilIdle()

        val progress = manager.stateOf(ASSET)
        assertEquals(MediaDownloadState.FAILED, progress.state)
        assertEquals(NetworkError.NO_INTERNET.getError(), progress.failureReason)
        assertTrue(progress.state.canResume(), "a failed transfer must be retryable")
    }

    // 📥 416 means the local .part is longer than the asset — start clean rather than stay stuck
    @Test
    fun `a rejected range restarts the download from zero`() = runTest {
        val store = FakePartStore()
        store.files[DESTINATION + PART_SUFFIX] = ByteArray(9999)
        val source = ConflictThenSucceedSource(TOTAL)
        val manager = manager(this, source, store)

        // 📥 size unknown, so the ranged request really goes out and gets its 416
        manager.enqueue(request(expected = 0))
        advanceUntilIdle()

        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
        assertEquals(2, source.fetches, "it should have retried exactly once")
        assertEquals(TOTAL.toInt(), store.files[DESTINATION]?.size)
    }

    @Test
    fun `a disk failure fails the transfer rather than looking like a pause`() = runTest {
        val store = FakePartStore()
        store.failAppend = true
        val manager = manager(this, FakeByteSource(TOTAL), store)

        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(MediaDownloadState.FAILED, manager.stateOf(ASSET).state)
    }

    @Test
    fun `transfers past the parallel limit wait their turn`() = runTest {
        val store = FakePartStore()
        val source = FakeByteSource(TOTAL)
        source.gate = CompletableDeferred()
        val manager = manager(this, source, store, maxParallel = 1)

        manager.enqueue(request())
        advanceUntilIdle()
        manager.enqueue(request().copy(assetId = "asset-2", destinationRelativePath = "imported/note-1/a2.mp4"))
        advanceUntilIdle()

        assertEquals(MediaDownloadState.DOWNLOADING, manager.stateOf(ASSET).state)
        assertEquals(MediaDownloadState.QUEUED, manager.stateOf("asset-2").state)

        source.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf("asset-2").state)
    }

    // 📥 two cards showing one attachment must SHARE the transfer, never both write the .part
    @Test
    fun `enqueueing the same asset twice starts only one transfer`() = runTest {
        val store = FakePartStore()
        val source = FakeByteSource(TOTAL)
        source.gate = CompletableDeferred()
        val manager = manager(this, source, store)

        manager.enqueue(request())
        advanceUntilIdle()
        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(1, source.fetchCount, "the second enqueue started a second writer")

        source.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(TOTAL.toInt(), store.files[DESTINATION]?.size, "the part file was written twice")
    }

    // 📥 the row learns the path BEFORE the card says downloaded — or the player has nothing to open
    @Test
    fun `a finished download is landed on its holder before it reports downloaded`() = runTest {
        val landings = mutableListOf<Triple<String, String, String>>()
        lateinit var manager: MediaDownloadManager
        var stateWhenLanded: MediaDownloadState? = null
        val handler = landing { assetId, holderId, path ->
            landings += Triple(assetId, holderId, path)
            stateWhenLanded = manager.stateOf(ASSET).state
        }
        manager = manager(this, FakeByteSource(TOTAL), FakePartStore(), handlers = listOf(handler))

        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(listOf(Triple(ASSET, HOLDER, "/storage/$DESTINATION")), landings)
        assertTrue(stateWhenLanded != MediaDownloadState.DOWNLOADED, "the card flipped before the row knew")
        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
    }

    // 📥 the app died between the last byte and the rename — promote it, never ask the server again
    @Test
    fun `a part file that is already whole is promoted without a request`() = runTest {
        val store = FakePartStore()
        store.files[DESTINATION + PART_SUFFIX] = ByteArray(TOTAL.toInt())
        val source = FakeByteSource(TOTAL)
        val manager = manager(this, source, store)

        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(0, source.fetchCount, "a whole part must not be fetched again")
        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
        assertEquals(TOTAL.toInt(), store.files[DESTINATION]?.size)
    }

    // 📥 bytes on disk but the row lost its path — re-land from disk, never re-download
    @Test
    fun `a file already at its destination is re-landed, not downloaded again`() = runTest {
        val store = FakePartStore()
        store.files[DESTINATION] = ByteArray(TOTAL.toInt())
        val source = FakeByteSource(TOTAL)
        var landed = 0
        val manager = manager(this, source, store, handlers = listOf(landing { _, _, _ -> landed++ }))

        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(0, source.fetchCount)
        assertEquals(1, landed)
        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
    }

    // ⏸️ sync re-queues missing files every cycle — it must never undo a pause the person chose
    @Test
    fun `an automatic enqueue does not resume a paused transfer`() = runTest {
        val store = FakePartStore()
        val source = FakeByteSource(TOTAL)
        source.gate = CompletableDeferred()
        val manager = manager(this, source, store)

        manager.enqueue(request())
        advanceUntilIdle()
        manager.pause(ASSET)
        advanceUntilIdle()
        val fetchesWhenPaused = source.fetchCount

        manager.enqueue(request(), automatic = true)
        advanceUntilIdle()

        assertEquals(MediaDownloadState.PAUSED, manager.stateOf(ASSET).state)
        assertEquals(fetchesWhenPaused, source.fetchCount, "sync resumed a paused download")
    }

    // 📥 a landing handler that throws must not strand the transfer — the bytes are safe on disk
    @Test
    fun `a failing landing handler still leaves the file downloaded`() = runTest {
        val handler = landing { _, _, _ -> throw IllegalStateException("db locked") }
        val store = FakePartStore()
        val manager = manager(this, FakeByteSource(TOTAL), store, handlers = listOf(handler))

        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
        assertEquals(TOTAL.toInt(), store.files[DESTINATION]?.size)
    }

    // 🗑️ sync re-queues every ~20s — a cancel must stick until the person starts it again
    @Test
    fun `a cancelled download is not revived by sync, only by the person`() = runTest {
        val store = FakePartStore()
        val source = FakeByteSource(TOTAL)
        source.gate = CompletableDeferred()
        val manager = manager(this, source, store)

        manager.enqueue(request())
        advanceUntilIdle()
        manager.cancel(ASSET)
        advanceUntilIdle()

        manager.enqueue(request(), automatic = true)
        advanceUntilIdle()
        assertEquals(1, source.fetchCount, "sync brought a cancelled download back")
        assertEquals(MediaDownloadState.NOT_DOWNLOADED, manager.stateOf(ASSET).state)

        source.gate = null
        manager.enqueue(request())
        advanceUntilIdle()
        assertEquals(MediaDownloadState.DOWNLOADED, manager.stateOf(ASSET).state)
    }

    // 📥 a stream that ends early must not become a "finished" file — keep the bytes and resume later
    @Test
    fun `a download that ends short fails and keeps its part`() = runTest {
        val store = FakePartStore()
        val manager = manager(this, ShortSource(total = TOTAL, deliver = TOTAL / 2), store)

        manager.enqueue(request())
        advanceUntilIdle()

        assertEquals(MediaDownloadState.FAILED, manager.stateOf(ASSET).state)
        assertNull(store.files[DESTINATION], "a half file was promoted")
        assertEquals((TOTAL / 2).toInt(), store.files[DESTINATION + PART_SUFFIX]?.size, "the part was thrown away")
    }

    // 📥 a file that arrived through eager sync must never offer a Download button
    @Test
    fun `markDownloaded reports a file that is already here`() = runTest {
        val manager = manager(this, FakeByteSource(TOTAL), FakePartStore())
        manager.markDownloaded(ASSET, TOTAL)

        val progress = manager.stateOf(ASSET)
        assertTrue(progress.state.isReady())
        assertEquals(100, progress.percent)
    }

    @Test
    fun `an unknown asset reports itself as not downloaded`() {
        val scope = TestScope()
        val manager = manager(scope, FakeByteSource(TOTAL), FakePartStore())
        val progress = manager.stateOf("nobody-asked", totalBytes = TOTAL)
        assertEquals(MediaDownloadState.NOT_DOWNLOADED, progress.state)
        assertEquals(TOTAL, progress.totalBytes)
    }
}
