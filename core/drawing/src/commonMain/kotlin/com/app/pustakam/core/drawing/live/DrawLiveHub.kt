package com.app.pustakam.core.drawing.live

class DrawLiveHub(private val send: (String) -> Boolean) {

    private val rooms = LinkedHashMap<String, DrawLiveRoom>()

    fun rooms(): Map<String, DrawLiveRoom> = rooms.toMap()

    fun room(roomId: String): DrawLiveRoom? = rooms[roomId]

    fun join(roomId: String): DrawLiveRoom {
        rooms[roomId]?.takeIf { it.status != DrawLiveStatus.CLOSED }?.let { return it }
        val room = DrawLive.joining(roomId)
        rooms[roomId] = room
        send(DrawLiveProtocol.encode(DrawLiveRequest.Join(roomId)))
        return room
    }

    fun leave(roomId: String) {
        val room = rooms.remove(roomId) ?: return
        DrawLive.leaving(room).forEach { send(DrawLiveProtocol.encode(it)) }
    }

    fun request(request: DrawLiveRequest): Boolean {
        val room = rooms[request.roomId] ?: return false
        if (!DrawLive.isLive(room)) return false
        return send(DrawLiveProtocol.encode(request))
    }

    fun onConnected() {
        rooms.keys.forEach { roomId -> send(DrawLiveProtocol.encode(DrawLiveRequest.Join(roomId))) }
    }

    fun onDisconnected() {
        rooms.keys.toList().forEach { roomId ->
            rooms[roomId] = DrawLive.reduce(rooms.getValue(roomId), DrawLiveEvent.Disconnected(roomId))
        }
    }

    fun onFrame(raw: String): DrawLiveEvent? {
        val event = DrawLiveProtocol.decode(raw) ?: return null
        val room = rooms[event.roomId] ?: return null
        rooms[event.roomId] = DrawLive.reduce(room, event)
        return event
    }
}
