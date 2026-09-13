// 🔧 05-Sep-2026 — NSMutableURLRequest(uRL=), NSURL(string=) and NSURLSessionWebSocketMessage(string=)
//   are Objective-C INITIALISERS, and calling one from Kotlin needs BetaInteropApi (same reason
//   IosFileSystem.kt opts in for NSData.create). File-level, because three call sites need it.
@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.app.pustakam.feature.chat.domain.socket

import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.setValue

// 🔧 05-Sep-2026 — wildcard, deliberately, and only here.
//
//   Every method this file calls on NSURLSession / NSURLSessionWebSocketTask comes from an
//   Objective-C CATEGORY, and cinterop decides per category whether to emit its methods as members
//   of the class or as top-level extension functions. You cannot tell which from the header, and
//   the two failure modes look nothing alike: name it when it is a member and you get
//   "Unresolved reference 'webSocketTaskWithRequest'" on the IMPORT line; leave it unnamed when it
//   is an extension and `setValue` silently resolves to the stdlib's property-delegate operators
//   and blames those instead.
//
//   Naming them one at a time cost three build round-trips for setValue and webSocketTaskWithRequest
//   alone, with receiveMessageWithCompletionHandler, sendMessage and cancelWithCloseCode still
//   unproven behind the first error. The wildcard settles all six at once and cannot go stale when
//   a future SDK moves a method between a category and the main @interface.
import platform.Foundation.*

private const val NORMAL_CLOSURE = 1000L
private const val UNKNOWN_FAILURE = "Connection failed"

/**
 * 💬 NSURLSessionWebSocketTask (iOS 13+; this app targets 18). Ktor 2.3.12's Darwin engine cannot
 * do websockets at all, which is the whole reason this file exists — see ChatSocket.kt.
 * Receiving is a self-rearming callback: each receiveMessage delivers exactly ONE frame and you
 * must ask again for the next. Forgetting the re-arm is the classic bug here — the socket connects,
 * the first message arrives, and everything after it silently never does.
 */
actual fun platformChatSocketOpener(): ChatSocketOpener = ChatSocketOpener { url, token, listener ->
    val request = NSMutableURLRequest(uRL = NSURL(string = url))
    if (token.isNotBlank()) {
        request.setValue("$BEARER_PREFIX$token", forHTTPHeaderField = AUTH_HEADER)
    }

    val task = NSURLSession.sharedSession.webSocketTaskWithRequest(request)
    val connection = DarwinChatSocket(task, listener)
    connection.start()
    connection
}

private class DarwinChatSocket(
    private val task: NSURLSessionWebSocketTask,
    private val listener: ChatSocketListener,
) : ChatSocketConnection {

    // 🔌 a failure and a close both end the socket; without this guard the repository is told twice
    //   and schedules two reconnects, which is how the backoff turns into a connection storm.
    private var closed = false

    fun start() {
        task.resume()
        // NSURLSession has no "did open" callback on the task itself, so the socket counts as open
        // once it has been resumed; a handshake that fails surfaces through the receive callback.
        listener.onOpen()
        receiveNext()
    }

    private fun receiveNext() {
        if (closed) return
        task.receiveMessageWithCompletionHandler { message, error ->
            when {
                error != null -> finish(failure = true, reason = error.readableReason())
                message != null -> {
                    message.string?.let { listener.onText(it) }
                    // ⚠️ re-arm. One receive delivers one frame and nothing more.
                    receiveNext()
                }
                else -> finish(failure = true, reason = UNKNOWN_FAILURE)
            }
        }
    }

    override fun send(text: String): Boolean {
        if (closed) return false
        task.sendMessage(NSURLSessionWebSocketMessage(string = text)) { error ->
            if (error != null) finish(failure = true, reason = error.readableReason())
        }
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        task.cancelWithCloseCode(NORMAL_CLOSURE, null)
        listener.onClosed("closed")
    }

    private fun finish(failure: Boolean, reason: String) {
        if (closed) return
        closed = true
        task.cancelWithCloseCode(NORMAL_CLOSURE, null)
        if (failure) listener.onFailure(reason) else listener.onClosed(reason)
    }
}

private fun NSError.readableReason(): String = localizedDescription.ifBlank { UNKNOWN_FAILURE }
