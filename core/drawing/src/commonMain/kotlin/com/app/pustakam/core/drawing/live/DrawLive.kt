package com.app.pustakam.core.drawing.live

import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.model.models.share.NoteAccess

object DrawLiveReducer {

    fun reduce(room: DrawLiveRoom, event: DrawLiveEvent): DrawLiveRoom {
        if (event.roomId != room.roomId) return room
        return when (event) {
            is DrawLiveEvent.State -> room.copy(
                sessionId = event.sessionId,
                userId = event.userId,
                role = event.role,
                status = DrawLiveStatus.LIVE,
                members = event.members,
                locks = event.locks,
                strokes = room.strokes.filter { stroke -> event.locks.any { it.pageId == stroke.pageId && it.sessionId == stroke.sessionId } },
                denied = room.denied?.takeIf { denied -> event.locks.any { it.pageId == denied.pageId && it.sessionId == denied.sessionId } },
                closedReason = null
            )

            is DrawLiveEvent.Stroke -> streamed(room, event.stroke)

            is DrawLiveEvent.Op -> room.copy(
                strokes = room.strokes.filterNot { it.sessionId == event.sessionId && it.pageId == event.pageId }
            )

            is DrawLiveEvent.Denied -> room.copy(denied = event.lock)

            is DrawLiveEvent.Sync ->
                if (event.sessionId == room.sessionId) room else room.copy(syncRevision = room.syncRevision + 1)

            is DrawLiveEvent.Closed -> room.copy(
                status = DrawLiveStatus.CLOSED,
                locks = emptyList(),
                strokes = emptyList(),
                denied = null,
                closedReason = event.reason
            )

            is DrawLiveEvent.Disconnected ->
                if (room.status == DrawLiveStatus.CLOSED) room
                else room.copy(status = DrawLiveStatus.CONNECTING, locks = emptyList(), strokes = emptyList(), denied = null)
        }
    }

    private fun streamed(room: DrawLiveRoom, stroke: DrawLiveStroke): DrawLiveRoom {
        if (stroke.sessionId == room.sessionId) return room
        val previous = room.strokes.firstOrNull { it.sessionId == stroke.sessionId && it.pageId == stroke.pageId }
        if (previous != null && previous.seq >= stroke.seq) return room
        val others = room.strokes.filterNot { it.sessionId == stroke.sessionId && it.pageId == stroke.pageId }
        return room.copy(strokes = if (stroke.isEmpty) others else others + stroke)
    }
}

object DrawLive {

    private const val LABEL_MINE = "You are drawing"

    private const val LABEL_OTHER_DEVICE = "Drawing on your other device…"

    private const val LABEL_DRAWING = " is drawing…"

    private const val LABEL_SOMEONE = "Someone is drawing…"

    fun joining(roomId: String): DrawLiveRoom = DrawLiveRoom.joining(roomId)

    fun reduce(room: DrawLiveRoom, event: DrawLiveEvent): DrawLiveRoom = DrawLiveReducer.reduce(room, event)

    fun isLive(room: DrawLiveRoom): Boolean = room.status == DrawLiveStatus.LIVE

    fun canWrite(room: DrawLiveRoom): Boolean = isLive(room) && NoteAccess.canWrite(room.role)

    fun lockOn(room: DrawLiveRoom, pageId: String): DrawLiveLock? = room.locks.firstOrNull { it.pageId == pageId }

    fun holds(room: DrawLiveRoom, pageId: String): Boolean =
        room.sessionId.isNotEmpty() && lockOn(room, pageId)?.sessionId == room.sessionId

    fun canDrawOn(room: DrawLiveRoom, pageId: String): Boolean = canWrite(room) && holds(room, pageId)

    fun canClaim(room: DrawLiveRoom, pageId: String): Boolean {
        if (!canWrite(room)) return false
        val lock = lockOn(room, pageId) ?: return true
        return lock.sessionId == room.sessionId
    }

    fun heldPages(room: DrawLiveRoom): List<String> =
        room.locks.filter { room.sessionId.isNotEmpty() && it.sessionId == room.sessionId }.map { it.pageId }

    fun badgeOn(room: DrawLiveRoom, pageId: String): DrawLiveBadge? {
        val lock = lockOn(room, pageId) ?: return null
        val mine = lock.sessionId == room.sessionId
        val name = room.members.firstOrNull { it.sessionId == lock.sessionId }?.name.orEmpty()
        return DrawLiveBadge(pageId, name, mine, !mine && lock.userId == room.userId)
    }

    fun badges(room: DrawLiveRoom): List<DrawLiveBadge> = room.locks.mapNotNull { badgeOn(room, it.pageId) }

    fun badgeLabel(badge: DrawLiveBadge): String = when {
        badge.mine -> LABEL_MINE
        badge.otherDevice -> LABEL_OTHER_DEVICE
        badge.name.isNotBlank() -> "${badge.name}$LABEL_DRAWING"
        else -> LABEL_SOMEONE
    }

    fun labelOn(room: DrawLiveRoom?, pageId: String): String? = room?.let { badgeOn(it, pageId) }?.let(::badgeLabel)

    fun inputPage(room: DrawLiveRoom?): String? = room?.let { heldPages(it).firstOrNull() }

    fun acceptsInput(room: DrawLiveRoom?): Boolean = room == null || inputPage(room) != null

    fun previews(room: DrawLiveRoom): List<DrawElement> = room.strokes.flatMap { it.elements }

    fun hiddenIds(room: DrawLiveRoom): Set<String> = room.strokes.flatMapTo(mutableSetOf()) { it.hiddenIds }

    fun claims(room: DrawLiveRoom, focusedPageId: String?, drawing: Boolean, busy: Boolean): List<DrawLiveRequest> {
        if (busy || !isLive(room)) return emptyList()
        val held = heldPages(room)
        val target = focusedPageId?.takeIf { drawing && canWrite(room) }
        val releases = held.filter { it != target }.map { DrawLiveRequest.Unlock(room.roomId, it) }
        val claim = target?.takeIf { it !in held && canClaim(room, it) }?.let { DrawLiveRequest.Lock(room.roomId, it) }
        return releases + listOfNotNull(claim)
    }

    fun leaving(room: DrawLiveRoom): List<DrawLiveRequest> =
        heldPages(room).map { DrawLiveRequest.Unlock(room.roomId, it) } + DrawLiveRequest.Leave(room.roomId)
}
