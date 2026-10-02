package com.app.pustakam.core.drawing.live

import com.app.pustakam.core.drawing.editor.DrawEditorState

class DrawLiveOutbox(private val roomId: String) {

    private var seq = 0L

    private var seenOps: Long? = null

    private var streamedAt: Long? = null

    private var streaming = false

    private var claimed: String? = null

    private var claimedAt = 0L

    fun claims(room: DrawLiveRoom, focusedPageId: String?, drawing: Boolean, busy: Boolean, now: Long): List<DrawLiveRequest> =
        DrawLive.claims(room, focusedPageId, drawing, busy).filter { request ->
            if (request !is DrawLiveRequest.Lock) return@filter true
            val repeated = request.pageId == claimed && now - claimedAt < CLAIM_RETRY_MILLIS
            if (!repeated) {
                claimed = request.pageId
                claimedAt = now
            }
            !repeated
        }

    fun next(state: DrawEditorState, pageId: String?, now: Long): List<DrawLiveRequest> {
        val previousOps = seenOps
        seenOps = state.opSequence
        if (pageId == null) return emptyList()
        val requests = mutableListOf<DrawLiveRequest>()
        val committed = state.lastOp?.takeIf { previousOps != null && state.opSequence > previousOps }
        if (committed != null) requests.add(DrawLiveRequest.Op(roomId, pageId, committed))
        val preview = state.preview
        val drawing = !preview.isEmpty
        val last = streamedAt
        when {
            drawing && (last == null || now - last >= STREAM_INTERVAL_MILLIS) -> {
                requests.add(DrawLiveRequest.Stroke(roomId, pageId, ++seq, preview.elements, preview.hiddenIds))
                streamedAt = now
                streaming = true
            }

            !drawing && streaming -> {
                requests.add(DrawLiveRequest.Stroke(roomId, pageId, ++seq, emptyList(), emptySet()))
                streamedAt = null
                streaming = false
            }
        }
        return requests
    }

    companion object {
        const val STREAM_INTERVAL_MILLIS = 50L

        const val CLAIM_RETRY_MILLIS = 1_500L
    }
}
