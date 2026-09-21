package com.app.pustakam.core.media.download

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.NetworkError
import kotlinx.coroutines.CompletableDeferred

// 📥 hands over chunks one at a time and can be held open mid-transfer, to test pausing
class FakeByteSource(
    private val totalBytes: Long,
    private val chunkSize: Int = 1024,
    private val failWith: Error? = null,
) : MediaByteSource {

    var lastFromByte: Long = -1
        private set
    var fetchCount: Int = 0
        private set

    // 📥 completed by the test to let the transfer past its halfway point
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun fetch(
        ownerId: String,
        assetId: String,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error> {
        lastFromByte = fromByte
        fetchCount++
        failWith?.let { return Result.Error(it) }

        var soFar = fromByte
        var gateOpened = false
        while (soFar < totalBytes) {
            val size = minOf(chunkSize.toLong(), totalBytes - soFar).toInt()
            soFar += size
            onChunk(ByteArray(size) { 1 }, soFar, totalBytes)
            if (!gateOpened && soFar >= totalBytes / 2) {
                gateOpened = true
                gate?.await()
            }
        }
        return Result.Success(soFar)
    }
}

// 📥 an in-memory stand-in for app storage, so the manager is tested without a file system
class FakePartStore : MediaPartStore {

    val files: MutableMap<String, ByteArray> = mutableMapOf()
    var failAppend = false
    var failPromote = false

    override fun bytesOnDisk(partRelativePath: String): Long =
        files[partRelativePath]?.size?.toLong() ?: 0

    override fun append(partRelativePath: String, bytes: ByteArray): Boolean {
        if (failAppend) return false
        files[partRelativePath] = (files[partRelativePath] ?: ByteArray(0)) + bytes
        return true
    }

    override fun promote(partRelativePath: String, destinationRelativePath: String): Boolean {
        if (failPromote) return false
        val bytes = files.remove(partRelativePath) ?: return false
        files[destinationRelativePath] = bytes
        return true
    }

    override fun discard(partRelativePath: String) {
        files.remove(partRelativePath)
    }

    override fun absolutePathOf(relativePath: String): String = "/storage/$relativePath"
}

// 📥 answers the first ranged request with a 416 (CONFLICT), then serves the whole asset
class ConflictThenSucceedSource(private val totalBytes: Long) : MediaByteSource {

    var fetches: Int = 0
        private set

    override suspend fun fetch(
        ownerId: String,
        assetId: String,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error> {
        fetches++
        if (fromByte > 0) return Result.Error(NetworkError.CONFLICT)
        onChunk(ByteArray(totalBytes.toInt()) { 1 }, totalBytes, totalBytes)
        return Result.Success(totalBytes)
    }
}

// 📥 a landing handler from a lambda — a plain interface cannot be SAM-converted when its method suspends
fun landing(block: suspend (assetId: String, holderId: String, absolutePath: String) -> Unit): MediaLandingHandler =
    object : MediaLandingHandler {
        override suspend fun onLanded(assetId: String, holderId: String, absolutePath: String) =
            block(assetId, holderId, absolutePath)
    }

// 📥 announces [total] but the stream ends after [deliver] bytes without an error
class ShortSource(private val total: Long, private val deliver: Long) : MediaByteSource {
    override suspend fun fetch(
        ownerId: String,
        assetId: String,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error> {
        val size = (deliver - fromByte).toInt()
        onChunk(ByteArray(size) { 1 }, deliver, total)
        return Result.Success(deliver)
    }
}
