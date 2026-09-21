package com.app.pustakam.core.media.download

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.NetworkError
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.media.model.MediaDownloadProgress
import com.app.pustakam.core.media.model.MediaDownloadRequest
import com.app.pustakam.core.media.model.MediaDownloadState
import com.app.pustakam.core.media.progress.ThroughputMeter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val PART_SUFFIX = ".part"
private const val TAG = "MediaDownloadManager"
private const val DEFAULT_PARALLEL = 2
private const val EMIT_EVERY_MILLIS = 200L
private const val DISK_FAILURE = "Could not save the file. Check your storage."
private const val INCOMPLETE = "The file arrived incomplete."

// 📥 every attachment transfer in one place — cards and sync call the verbs, [handlers] hear when bytes land
class MediaDownloadManager(
    private val source: MediaByteSource,
    private val store: MediaPartStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val maxParallel: Int = DEFAULT_PARALLEL,
    private val handlers: List<MediaLandingHandler> = emptyList(),
) {

    private val _progress = MutableStateFlow<Map<String, MediaDownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, MediaDownloadProgress>> = _progress.asStateFlow()

    // 🔒 every map below is touched from several coroutines, so they all live behind this one mutex
    private val lock = Mutex()
    private val jobs = mutableMapOf<String, Job>()
    private val requests = mutableMapOf<String, MediaDownloadRequest>()
    private val waiting = mutableListOf<String>()
    // ⏸️ jobs being cancelled right now — a resume waits for them, or two writers share one .part
    private val stopping = mutableMapOf<String, Job>()
    // 🗑️ cancelled by the person this session — sync's automatic re-queue must not bring them back
    private val cancelled = mutableSetOf<String>()

    // 📥 what a card binds to; an asset nobody has touched reports itself as not downloaded
    fun stateOf(assetId: String, totalBytes: Long = 0): MediaDownloadProgress =
        _progress.value[assetId] ?: MediaDownloadProgress.notDownloaded(assetId, totalBytes)

    // 📥 where a finished asset lives, in the absolute form playback and rendering expect
    fun absolutePathOf(relativePath: String): String = store.absolutePathOf(relativePath)

    // 📥 the file is already on disk — eager sync landed it, so the card must not offer a download
    fun markDownloaded(assetId: String, totalBytes: Long) {
        if (_progress.value[assetId]?.state == MediaDownloadState.DOWNLOADED) return
        put(MediaDownloadProgress.downloaded(assetId, totalBytes))
    }

    // 📥 start unless already running; [automatic] (sync) never undoes a user's pause, and DOWNLOADED re-lands
    fun enqueue(request: MediaDownloadRequest, automatic: Boolean = false) {
        val current = _progress.value[request.assetId]?.state
        if (current == MediaDownloadState.DOWNLOADING) return
        if (automatic && current == MediaDownloadState.PAUSED) return
        scope.launch { admit(request, automatic) }
    }

    // ⏸️ cancel the coroutine but KEEP the .part file — that is what makes resume cheap
    fun pause(assetId: String) {
        scope.launch {
            val job = lock.withLock {
                waiting.remove(assetId)
                jobs.remove(assetId)?.also { stopping[assetId] = it }
            }
            // ⏸️ WAIT for it: a chunk still in flight would otherwise publish DOWNLOADING after this
            job?.cancelAndJoin()
            lock.withLock { if (stopping[assetId] === job) stopping.remove(assetId) }
            _progress.update { all ->
                val current = all[assetId] ?: return@update all
                if (current.state == MediaDownloadState.DOWNLOADED) all
                else all + (assetId to current.copy(state = MediaDownloadState.PAUSED, bytesPerSecond = 0))
            }
            startNextWaiting()
        }
    }

    fun resume(assetId: String) {
        scope.launch { lock.withLock { requests[assetId] }?.let { admit(it, automatic = false) } }
    }

    // 🗑️ the user gave up — the partial bytes go too, or they leak until the app is reinstalled
    fun cancel(assetId: String) {
        scope.launch {
            val (job, request) = lock.withLock {
                waiting.remove(assetId)
                cancelled += assetId
                jobs.remove(assetId)?.also { stopping[assetId] = it } to requests.remove(assetId)
            }
            // 🗑️ join BEFORE discarding, or an in-flight append re-creates the .part we just deleted
            job?.cancelAndJoin()
            lock.withLock { if (stopping[assetId] === job) stopping.remove(assetId) }
            request?.let { store.discard(it.destinationRelativePath + PART_SUFFIX) }
            _progress.update { it - assetId }
            startNextWaiting()
        }
    }

    private suspend fun admit(request: MediaDownloadRequest, automatic: Boolean) {
        val stoppingJob = lock.withLock {
            // 🗑️ sync never revives a cancel; the person starting it again clears the mark
            if (automatic && request.assetId in cancelled) return
            if (!automatic) cancelled -= request.assetId
            stopping[request.assetId]
        }
        // ⏸️ a job still stopping may be mid-append — read the .part length only once it is done
        stoppingJob?.join()

        // 📥 already whole on disk — a previous run finished but its row never heard. Land, don't fetch.
        val finished = store.bytesOnDisk(request.destinationRelativePath)
        if (finished > 0 && (request.expectedBytes <= 0 || finished == request.expectedBytes)) {
            land(request, finished)
            return
        }

        val alreadyHere = store.bytesOnDisk(request.destinationRelativePath + PART_SUFFIX)
        // 📥 LAZY and started only once it is in the map, so a fast finish cannot race its own bookkeeping
        val job = scope.launch(start = CoroutineStart.LAZY) { transfer(request, alreadyHere) }
        val outcome = lock.withLock {
            requests[request.assetId] = request
            when {
                // 📥 already running: two cards showing one attachment share the transfer, never race it
                jobs.containsKey(request.assetId) -> Admission.ALREADY_RUNNING
                jobs.size >= maxParallel -> {
                    if (!waiting.contains(request.assetId)) waiting += request.assetId
                    Admission.QUEUED
                }
                else -> {
                    waiting.remove(request.assetId)
                    jobs[request.assetId] = job
                    Admission.STARTED
                }
            }
        }
        if (outcome != Admission.STARTED) {
            job.cancel()
            if (outcome == Admission.QUEUED) {
                put(
                    MediaDownloadProgress(
                        assetId = request.assetId,
                        state = MediaDownloadState.QUEUED,
                        bytesDownloaded = alreadyHere,
                        totalBytes = request.expectedBytes,
                    )
                )
            }
            return
        }
        job.invokeOnCompletion { releaseSlot(request.assetId, job) }
        job.start()
    }

    private suspend fun transfer(request: MediaDownloadRequest, startFrom: Long) {
        val partPath = request.destinationRelativePath + PART_SUFFIX
        val meter = ThroughputMeter()
        var lastEmitAt = 0L
        var latest = MediaDownloadProgress(
            assetId = request.assetId,
            state = MediaDownloadState.DOWNLOADING,
            bytesDownloaded = startFrom,
            totalBytes = request.expectedBytes,
        )
        put(latest)

        // 📥 the part is already whole (the app died before the rename) — promote it; asking the server would 416
        if (startFrom > 0 && request.expectedBytes > 0 && startFrom >= request.expectedBytes) {
            if (startFrom == request.expectedBytes) {
                finish(request, partPath, latest, startFrom)
            } else {
                store.discard(partPath)
                transfer(request, 0)
            }
            return
        }

        var writeFailed = false
        val outcome = try {
            source.fetch(request.ownerId, request.assetId, startFrom) { chunk, soFar, total ->
                // ⏸️ a pause lands between chunks — stop before writing, not after
                currentCoroutineContext().ensureActive()
                if (!store.append(partPath, chunk)) {
                    writeFailed = true
                    throw CancellationException("could not write $partPath")
                }
                val now = clock()
                latest = latest.copy(
                    bytesDownloaded = soFar,
                    totalBytes = if (total > 0) total else latest.totalBytes,
                    bytesPerSecond = meter.sample(soFar, now),
                )
                // 📥 a 64 KB chunk lands far faster than a screen redraws — throttle or the UI thrashes
                if (now - lastEmitAt >= EMIT_EVERY_MILLIS) {
                    lastEmitAt = now
                    put(latest)
                }
            }
        } catch (e: CancellationException) {
            // ⏸️ pause() cancels this job, which is not a failure; only a failed WRITE is
            if (!writeFailed) throw e
            fail(latest, DISK_FAILURE)
            return
        }

        when (outcome) {
            is Result.Success -> finish(request, partPath, latest, outcome.data)
            is Result.Error -> retryOrFail(request, partPath, latest, outcome.error)
            Result.Loading -> Unit
        }
    }

    private suspend fun finish(
        request: MediaDownloadRequest,
        partPath: String,
        latest: MediaDownloadProgress,
        bytes: Long,
    ) {
        // 📥 never promote a file that is not whole: short keeps the .part to resume, long is corrupt and goes
        val expected = if (latest.totalBytes > 0) latest.totalBytes else request.expectedBytes
        val onDisk = store.bytesOnDisk(partPath)
        if (expected > 0 && onDisk != expected) {
            if (onDisk > expected) store.discard(partPath)
            fail(latest.copy(bytesDownloaded = minOf(onDisk, expected)), INCOMPLETE)
            return
        }
        if (!store.promote(partPath, request.destinationRelativePath)) {
            fail(latest, DISK_FAILURE)
            return
        }
        land(request, bytes)
    }

    // 📥 the row learns the path BEFORE the card flips to downloaded, or the player has nothing to open
    private suspend fun land(request: MediaDownloadRequest, bytes: Long) {
        val absolutePath = store.absolutePathOf(request.destinationRelativePath)
        handlers.forEach { handler ->
            try {
                handler.onLanded(request.assetId, request.holderId, absolutePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 📥 the bytes are safe on disk; the next sync re-admits and re-lands them
                log_d(TAG, "landing ${request.assetId} failed: $e")
            }
        }
        lock.withLock {
            requests.remove(request.assetId)
            waiting.remove(request.assetId)
        }
        put(
            MediaDownloadProgress(
                assetId = request.assetId,
                state = MediaDownloadState.DOWNLOADED,
                bytesDownloaded = bytes,
                totalBytes = if (request.expectedBytes > 0) request.expectedBytes else bytes,
            )
        )
    }

    // 📥 a 416 (mapped to CONFLICT) means the .part is longer than the asset — start clean, once
    private suspend fun retryOrFail(
        request: MediaDownloadRequest,
        partPath: String,
        latest: MediaDownloadProgress,
        error: Error,
    ) {
        if (error == NetworkError.CONFLICT && latest.bytesDownloaded > 0) {
            store.discard(partPath)
            transfer(request, 0)
            return
        }
        fail(latest, (error as? NetworkError)?.getError())
    }

    private fun fail(latest: MediaDownloadProgress, reason: String?) {
        put(latest.copy(state = MediaDownloadState.FAILED, bytesPerSecond = 0, failureReason = reason))
    }

    private fun releaseSlot(assetId: String, finished: Job) {
        scope.launch {
            // 📥 only clear the slot if it is still OURS — a resume may already have claimed it
            lock.withLock { if (jobs[assetId] === finished) jobs.remove(assetId) }
            startNextWaiting()
        }
    }

    // 📥 a queued entry whose request is gone leaves the queue, and the NEXT one still gets its turn
    private suspend fun startNextWaiting() {
        while (true) {
            var stale = false
            val request = lock.withLock {
                if (jobs.size >= maxParallel) return@withLock null
                val next = waiting.firstOrNull() ?: return@withLock null
                requests[next] ?: null.also { waiting.remove(next); stale = true }
            }
            if (request != null) {
                admit(request, automatic = false)
                return
            }
            if (!stale) return
        }
    }

    private enum class Admission { STARTED, QUEUED, ALREADY_RUNNING }

    // 🔒 update{} not value= : two transfers emitting at once would otherwise drop each other
    private fun put(value: MediaDownloadProgress) {
        _progress.update { it + (value.assetId to value) }
    }
}
