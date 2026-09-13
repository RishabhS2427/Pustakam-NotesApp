package com.app.pustakam.feature.chat.domain.repository

import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.SocketReadPayload
import com.app.pustakam.core.model.models.chat.SocketSendPayload
import com.app.pustakam.core.model.models.chat.SocketTypingPayload
import com.app.pustakam.feature.chat.domain.socket.ChatSocketEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 💬 Pure transport. It knows how to hold a websocket open and how to turn frames into events —
 * nothing about conversations, storage or unread counts.
 *
 * The dependency runs ONE way: ChatRepository collects [events]. The socket never calls back into
 * the repository, which is what keeps these two out of a cycle.
 */
interface IChatSocketRepository {

    val connectionState: StateFlow<ChatConnectionState>

    /** Replay 0 on purpose — an event that arrived before the screen opened is history, not news. */
    val events: SharedFlow<ChatSocketEvent>

    fun connect()

    fun disconnect()

    /** Foreground keeps the socket up; background lets it drop rather than burn the battery. */
    fun setForeground(isForeground: Boolean)

    /** False when the socket is down, which is the caller's cue to fall back to REST. */
    fun send(frameId: String, payload: SocketSendPayload): Boolean

    fun sendTyping(payload: SocketTypingPayload): Boolean

    fun sendRead(frameId: String, payload: SocketReadPayload): Boolean
}
