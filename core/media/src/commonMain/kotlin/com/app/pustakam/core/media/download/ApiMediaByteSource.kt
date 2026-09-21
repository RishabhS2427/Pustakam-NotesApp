package com.app.pustakam.core.media.download

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.network.ApiCallClient

// 📥 the real network seam — the only class in :core:media that knows an HTTP client exists
class ApiMediaByteSource(private val apiClient: ApiCallClient) : MediaByteSource {

    override suspend fun fetch(
        ownerId: String,
        assetId: String,
        fromByte: Long,
        onChunk: suspend (chunk: ByteArray, bytesSoFar: Long, totalBytes: Long) -> Unit,
    ): Result<Long, Error> = apiClient.downloadMediaStream(ownerId, assetId, fromByte, onChunk)
}
