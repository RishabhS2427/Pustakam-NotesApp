package com.app.pustakam.core.drawing.live

import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.model.models.share.NoteRole

enum class DrawLiveStatus { CONNECTING, LIVE, CLOSED }

data class DrawLiveMember(
    val sessionId: String,
    val userId: String,
    val name: String,
    val role: NoteRole,
    val focusPageId: String?
)

data class DrawLiveLock(
    val pageId: String,
    val sessionId: String,
    val userId: String,
    val since: Long
)

data class DrawLiveStroke(
    val sessionId: String,
    val pageId: String,
    val seq: Long,
    val elements: List<DrawElement>,
    val hiddenIds: Set<String>
) {
    val isEmpty: Boolean get() = elements.isEmpty() && hiddenIds.isEmpty()
}

data class DrawLiveBadge(
    val pageId: String,
    val name: String,
    val mine: Boolean,
    val otherDevice: Boolean
)

data class DrawLiveRoom(
    val roomId: String,
    val sessionId: String,
    val userId: String,
    val role: NoteRole,
    val status: DrawLiveStatus,
    val members: List<DrawLiveMember>,
    val locks: List<DrawLiveLock>,
    val strokes: List<DrawLiveStroke>,
    val denied: DrawLiveLock?,
    val syncRevision: Long,
    val closedReason: String?
) {
    companion object {
        fun joining(roomId: String): DrawLiveRoom = DrawLiveRoom(
            roomId = roomId,
            sessionId = "",
            userId = "",
            role = NoteRole.READER,
            status = DrawLiveStatus.CONNECTING,
            members = emptyList(),
            locks = emptyList(),
            strokes = emptyList(),
            denied = null,
            syncRevision = 0L,
            closedReason = null
        )
    }
}

sealed class DrawLiveEvent {

    abstract val roomId: String

    data class State(
        override val roomId: String,
        val sessionId: String,
        val userId: String,
        val role: NoteRole,
        val members: List<DrawLiveMember>,
        val locks: List<DrawLiveLock>
    ) : DrawLiveEvent()

    data class Stroke(override val roomId: String, val stroke: DrawLiveStroke) : DrawLiveEvent()

    data class Op(override val roomId: String, val sessionId: String, val pageId: String, val op: DrawOp) : DrawLiveEvent()

    data class Denied(override val roomId: String, val lock: DrawLiveLock) : DrawLiveEvent()

    data class Sync(override val roomId: String, val sessionId: String) : DrawLiveEvent()

    data class Closed(override val roomId: String, val reason: String) : DrawLiveEvent()

    data class Disconnected(override val roomId: String) : DrawLiveEvent()
}

sealed class DrawLiveRequest {

    abstract val roomId: String

    data class Join(override val roomId: String) : DrawLiveRequest()

    data class Leave(override val roomId: String) : DrawLiveRequest()

    data class Focus(override val roomId: String, val pageId: String?) : DrawLiveRequest()

    data class Lock(override val roomId: String, val pageId: String) : DrawLiveRequest()

    data class Unlock(override val roomId: String, val pageId: String) : DrawLiveRequest()

    data class Stroke(
        override val roomId: String,
        val pageId: String,
        val seq: Long,
        val elements: List<DrawElement>,
        val hiddenIds: Set<String>
    ) : DrawLiveRequest()

    data class Op(override val roomId: String, val pageId: String, val op: DrawOp) : DrawLiveRequest()

    data class Sync(override val roomId: String) : DrawLiveRequest()
}
