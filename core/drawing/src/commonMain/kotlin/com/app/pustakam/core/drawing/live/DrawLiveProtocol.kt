package com.app.pustakam.core.drawing.live

import com.app.pustakam.core.drawing.codec.DrawCodec
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.model.models.share.NoteAccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object DrawLiveProtocol {

    const val PREFIX = "live_"

    const val JOIN = "live_join"
    const val LEAVE = "live_leave"
    const val FOCUS = "live_focus"
    const val LOCK = "live_lock"
    const val UNLOCK = "live_unlock"
    const val STROKE = "live_stroke"
    const val OP = "live_op"
    const val SYNC = "live_sync"
    const val STATE = "live_state"
    const val DENIED = "live_denied"
    const val CLOSED = "live_closed"

    private const val TYPE = "t"
    private const val DATA = "d"
    private const val ROOM = "roomId"
    private const val PAGE = "pageId"
    private const val SESSION = "sessionId"
    private const val USER = "userId"
    private const val ROLE = "role"
    private const val NAME = "name"
    private const val FOCUS_PAGE = "focusPageId"
    private const val SINCE = "since"
    private const val SEQ = "seq"
    private const val INK = "ink"
    private const val HIDDEN = "hidden"
    private const val OPERATION = "op"
    private const val YOU = "you"
    private const val MEMBERS = "members"
    private const val LOCKS = "locks"
    private const val LOCK_FIELD = "lock"
    private const val REASON = "reason"

    private const val NO_AUTHOR = ""

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isLiveFrame(type: String?): Boolean = type?.startsWith(PREFIX) == true

    fun encode(request: DrawLiveRequest): String {
        val room = ROOM to JsonPrimitive(request.roomId)
        val (type, fields) = when (request) {
            is DrawLiveRequest.Join -> JOIN to mapOf(room)
            is DrawLiveRequest.Leave -> LEAVE to mapOf(room)
            is DrawLiveRequest.Focus -> FOCUS to mapOf(room, PAGE to (request.pageId?.let(::JsonPrimitive) ?: JsonNull))
            is DrawLiveRequest.Lock -> LOCK to mapOf(room, PAGE to JsonPrimitive(request.pageId))
            is DrawLiveRequest.Unlock -> UNLOCK to mapOf(room, PAGE to JsonPrimitive(request.pageId))
            is DrawLiveRequest.Stroke -> STROKE to mapOf(
                room,
                PAGE to JsonPrimitive(request.pageId),
                SEQ to JsonPrimitive(request.seq),
                INK to JsonPrimitive(DrawCodec.encodeOp(DrawOp.Add(request.elements, NO_AUTHOR, request.seq))),
                HIDDEN to JsonArray(request.hiddenIds.sorted().map(::JsonPrimitive))
            )
            is DrawLiveRequest.Op -> OP to mapOf(
                room,
                PAGE to JsonPrimitive(request.pageId),
                OPERATION to JsonPrimitive(DrawCodec.encodeOp(request.op))
            )
            is DrawLiveRequest.Sync -> SYNC to mapOf(room)
        }
        return JsonObject(mapOf(TYPE to JsonPrimitive(type), DATA to JsonObject(fields))).toString()
    }

    fun decode(raw: String): DrawLiveEvent? {
        val envelope = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val type = envelope.text(TYPE)
        if (!isLiveFrame(type)) return null
        val data = envelope[DATA] as? JsonObject ?: return null
        val roomId = data.text(ROOM) ?: return null
        return runCatching { eventOf(type, roomId, data) }.getOrNull()
    }

    private fun eventOf(type: String?, roomId: String, data: JsonObject): DrawLiveEvent? = when (type) {
        STATE -> {
            val you = data[YOU] as? JsonObject
            DrawLiveEvent.State(
                roomId = roomId,
                sessionId = you?.text(SESSION).orEmpty(),
                userId = you?.text(USER).orEmpty(),
                role = NoteAccess.roleOf(you?.text(ROLE)),
                members = data.objects(MEMBERS).mapNotNull(::memberOf),
                locks = data.objects(LOCKS).mapNotNull(::lockOf)
            )
        }

        STROKE -> {
            val sessionId = data.text(SESSION)
            val pageId = data.text(PAGE)
            if (sessionId == null || pageId == null) {
                null
            } else {
                val ink = DrawCodec.decodeOp(data.text(INK)) as? DrawOp.Add
                DrawLiveEvent.Stroke(
                    roomId,
                    DrawLiveStroke(
                        sessionId = sessionId,
                        pageId = pageId,
                        seq = data.long(SEQ) ?: 0L,
                        elements = ink?.elements.orEmpty(),
                        hiddenIds = (data[HIDDEN] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()
                    )
                )
            }
        }

        OP -> {
            val sessionId = data.text(SESSION)
            val pageId = data.text(PAGE)
            val op = DrawCodec.decodeOp(data.text(OPERATION))
            if (sessionId == null || pageId == null || op == null) null else DrawLiveEvent.Op(roomId, sessionId, pageId, op)
        }

        DENIED -> (data[LOCK_FIELD] as? JsonObject)?.let(::lockOf)?.let { DrawLiveEvent.Denied(roomId, it) }

        SYNC -> DrawLiveEvent.Sync(roomId, data.text(SESSION).orEmpty())

        CLOSED -> DrawLiveEvent.Closed(roomId, data.text(REASON).orEmpty())

        else -> null
    }

    private fun memberOf(item: JsonObject): DrawLiveMember? {
        val sessionId = item.text(SESSION) ?: return null
        val userId = item.text(USER) ?: return null
        return DrawLiveMember(sessionId, userId, item.text(NAME).orEmpty(), NoteAccess.roleOf(item.text(ROLE)), item.text(FOCUS_PAGE))
    }

    private fun lockOf(item: JsonObject): DrawLiveLock? {
        val pageId = item.text(PAGE) ?: return null
        val sessionId = item.text(SESSION) ?: return null
        return DrawLiveLock(pageId, sessionId, item.text(USER).orEmpty(), item.long(SINCE) ?: 0L)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
