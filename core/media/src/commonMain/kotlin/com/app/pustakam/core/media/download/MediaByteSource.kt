package com.app.pustakam.core.media.download

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result

// 📥 the network seam — one function, so a test can drive the manager without a server
interface MediaByteSource {
    // 📥 [fromByte] > 0 resumes; returns the total byte count now on this device
    suspend fun fetch(
        ownerId: String,
        assetId: String,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error>
}
