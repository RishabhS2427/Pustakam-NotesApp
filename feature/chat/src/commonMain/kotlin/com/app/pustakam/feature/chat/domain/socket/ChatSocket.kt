package com.app.pustakam.feature.chat.domain.socket

/**
 * 💬 31-Aug-2026 — WHY THIS IS expect/actual AND NOT KTOR.
 *
 * The project is on Ktor 2.3.12, and 2.x's Darwin engine has no websocket support at all — that
 * arrived in Ktor 3. A shared `HttpClient { install(WebSockets) }` therefore compiles fine and
 * then fails on the iPhone. Rather than drag the whole network stack through a major upgrade for
 * one feature, the transport is platform-native — OkHttp on Android, NSURLSessionWebSocketTask on
 * iOS — behind this one interface. Everything above it, protocol included, stays shared Kotlin.
 *
 * If Ktor is ever upgraded to 3.x, this is the seam to collapse: replace the two actuals with one
 * Ktor implementation and nothing above changes.
 */
interface ChatSocketConnection {
    /** False when the socket is already closed, so the caller can queue instead of losing the send. */
    fun send(text: String): Boolean

    fun close()
}

/** Callbacks arrive on a platform thread; the repository is what moves them onto a coroutine. */
interface ChatSocketListener {
    fun onOpen()
    fun onText(text: String)
    fun onClosed(reason: String)
    fun onFailure(reason: String)
}

fun interface ChatSocketOpener {
    fun open(url: String, token: String, listener: ChatSocketListener): ChatSocketConnection
}

// Same shape as :core:network's platformHttpClient() — one seam per platform concern
expect fun platformChatSocketOpener(): ChatSocketOpener

/** The token goes in the header where a platform allows it, and in the query where it does not. */
const val TOKEN_QUERY_KEY = "token"
const val AUTH_HEADER = "Authorization"
const val BEARER_PREFIX = "Bearer "

fun socketUrlWithToken(url: String, token: String): String {
    if (token.isBlank()) return url
    val separator = if (url.contains('?')) "&" else "?"
    return "$url$separator$TOKEN_QUERY_KEY=$token"
}
