package com.app.pustakam.feature.chat.domain.socket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

private const val PING_INTERVAL_SECONDS = 20L
private const val NORMAL_CLOSURE = 1000
private const val CLOSING_MESSAGE = "bye"
private const val UNKNOWN_FAILURE = "Connection failed"

// 💬 One client for every chat socket in the process: OkHttp pools connections and runs its own
//   dispatcher threads, so a client per connection is pure waste.
private val socketClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        // OkHttp's own keep-alive ping. The server sends one too; both sides noticing a dead
        // link is what stops a phone that lost signal from looking permanently connected.
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

actual fun platformChatSocketOpener(): ChatSocketOpener = ChatSocketOpener { url, token, listener ->
    val request = Request.Builder()
        .url(url)
        // Android can set headers on the handshake, so the token does not need to sit in the URL
        .apply { if (token.isNotBlank()) addHeader(AUTH_HEADER, "$BEARER_PREFIX$token") }
        .build()

    val socket = socketClient.newWebSocket(request, object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

        override fun onMessage(webSocket: WebSocket, text: String) = listener.onText(text)

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
            listener.onClosed(reason.ifBlank { "closed ($code)" })

        // 🔌 OkHttp reports a dead socket here, NOT through onClosed — reconnect hangs off this
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            listener.onFailure(t.message ?: UNKNOWN_FAILURE)
    })

    object : ChatSocketConnection {
        override fun send(text: String): Boolean = socket.send(text)
        override fun close() {
            socket.close(NORMAL_CLOSURE, CLOSING_MESSAGE)
        }
    }
}
