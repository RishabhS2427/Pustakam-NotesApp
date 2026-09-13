package com.app.pustakam.feature.chat.data.repositoryImpl

import com.app.pustakam.core.common.coroutines.provideDispatcher
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.database.localdb.preferences.IAppPreferences
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.chat.SocketReadPayload
import com.app.pustakam.core.model.models.chat.SocketSendPayload
import com.app.pustakam.core.model.models.chat.SocketTypingPayload
import com.app.pustakam.core.network.getWebSocketUrl
import com.app.pustakam.feature.chat.domain.repository.IChatSocketRepository
import com.app.pustakam.feature.chat.domain.socket.ChatSocketConnection
import com.app.pustakam.feature.chat.domain.socket.ChatSocketEvent
import com.app.pustakam.feature.chat.domain.socket.ChatSocketListener
import com.app.pustakam.feature.chat.domain.socket.ChatSocketProtocol
import com.app.pustakam.feature.chat.domain.socket.platformChatSocketOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val EVENT_BUFFER = 64
private const val FIRST_BACKOFF_MILLIS = 1_000L
private const val MAX_BACKOFF_MILLIS = 30_000L
private const val BACKOFF_FACTOR = 2

/**
 * 💬 Holds one websocket open and turns frames into [ChatSocketEvent]s.
 *
 * It is a `single` in Koin on purpose: a second instance would open a second socket on the same
 * account, and the server would then deliver every message twice.
 */
internal class ChatSocketRepository : IChatSocketRepository, KoinComponent {

    private val prefs by inject<IAppPreferences>()

    private val scope = CoroutineScope(SupervisorJob() + provideDispatcher().io)
    private val opener = platformChatSocketOpener()

    // One connect at a time. Without this, foreground + connectivity + a manual retry arriving
    // together open three sockets and every message arrives three times.
    private val connectLock = Mutex()

    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    // extraBufferCapacity, not replay: an event that landed before the screen opened is history
    private val _events = MutableSharedFlow<ChatSocketEvent>(replay = 0, extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<ChatSocketEvent> = _events.asSharedFlow()

    private var connection: ChatSocketConnection? = null
    private var reconnectJob: Job? = null
    private var backoffMillis = FIRST_BACKOFF_MILLIS
    private var wantsConnection = false
    private var isForeground = true

    override fun connect() {
        wantsConnection = true
        scope.launch { openSocket() }
    }

    override fun disconnect() {
        wantsConnection = false
        reconnectJob?.cancel()
        reconnectJob = null
        connection?.close()
        connection = null
        _connectionState.value = ChatConnectionState.DISCONNECTED
    }

    /**
     * Backgrounding drops the socket rather than holding a radio open for hours. Coming back
     * connects immediately and resets the backoff — the user is looking at the screen now, so
     * making them wait out a 30-second delay from an earlier failure is wrong.
     */
    override fun setForeground(isForeground: Boolean) {
        this.isForeground = isForeground
        if (isForeground) {
            if (wantsConnection) {
                backoffMillis = FIRST_BACKOFF_MILLIS
                scope.launch { openSocket() }
            }
        } else {
            reconnectJob?.cancel()
            connection?.close()
            connection = null
            _connectionState.value = ChatConnectionState.DISCONNECTED
        }
    }

    override fun send(frameId: String, payload: SocketSendPayload): Boolean =
        write(ChatSocketProtocol.encodeSend(frameId, payload))

    override fun sendTyping(payload: SocketTypingPayload): Boolean =
        write(ChatSocketProtocol.encodeTyping(payload))

    override fun sendRead(frameId: String, payload: SocketReadPayload): Boolean =
        write(ChatSocketProtocol.encodeRead(frameId, payload))

    private fun write(frame: String): Boolean {
        val socket = connection ?: return false
        if (_connectionState.value != ChatConnectionState.CONNECTED) return false
        return socket.send(frame)
    }

    private suspend fun openSocket() {
        connectLock.withLock {
            if (!wantsConnection || !isForeground) return
            if (_connectionState.value == ChatConnectionState.CONNECTED) return
            if (connection != null) return

            val token = prefs.getAuthToken()?.takeIf { it.isNotBlank() }
            if (token == null) {
                // No token is not a network failure — retrying in a loop would never fix it
                log_d("ChatSocket", "no access token yet; not connecting")
                _connectionState.value = ChatConnectionState.DISCONNECTED
                return
            }

            _connectionState.value =
                if (backoffMillis > FIRST_BACKOFF_MILLIS) ChatConnectionState.RECONNECTING
                else ChatConnectionState.CONNECTING

            connection = opener.open(getWebSocketUrl(), token, SocketCallbacks())
        }
    }

    private inner class SocketCallbacks : ChatSocketListener {

        override fun onOpen() {
            _connectionState.value = ChatConnectionState.CONNECTED
            backoffMillis = FIRST_BACKOFF_MILLIS
            emit(ChatSocketEvent.Connected)
        }

        override fun onText(text: String) = emit(ChatSocketProtocol.decode(text))

        override fun onClosed(reason: String) = handleDrop(reason)

        override fun onFailure(reason: String) = handleDrop(reason)
    }

    private fun handleDrop(reason: String) {
        connection = null
        _connectionState.value = ChatConnectionState.DISCONNECTED
        emit(ChatSocketEvent.Disconnected(reason))
        scheduleReconnect()
    }

    /** Exponential backoff, capped. A server that is down must not be hammered by every device. */
    private fun scheduleReconnect() {
        if (!wantsConnection || !isForeground) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val wait = backoffMillis
            backoffMillis = (backoffMillis * BACKOFF_FACTOR).coerceAtMost(MAX_BACKOFF_MILLIS)
            delay(wait)
            openSocket()
        }
    }

    // tryEmit, not emit: a dropped event must never block the socket's callback thread
    private fun emit(event: ChatSocketEvent) {
        if (!_events.tryEmit(event)) log_d("ChatSocket", "event buffer full, dropped $event")
    }
}
