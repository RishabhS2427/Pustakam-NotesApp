package com.app.pustakam.feature.chat.domain.socket

import com.app.pustakam.core.model.models.chat.ChatMessageWire
import com.app.pustakam.core.model.models.chat.ReadReceiptWire

/**
 * 💬 Everything the server can say, as one closed set. Sealed on purpose: a new frame type on the
 * server becomes a compile error here rather than a message that silently never arrives.
 */
sealed interface ChatSocketEvent {

    data class Ready(val userId: String, val serverTime: Long) : ChatSocketEvent

    /** A message from anyone — including this user's own send arriving on their other device. */
    data class MessageReceived(val message: ChatMessageWire) : ChatSocketEvent

    /** The server took our send. `frameId` is what pairs it with the optimistic bubble. */
    data class Ack(
        val frameId: String?,
        val clientId: String?,
        val message: ChatMessageWire?,
        val duplicate: Boolean,
    ) : ChatSocketEvent

    data class Typing(val conversationId: String, val userId: String, val isTyping: Boolean) : ChatSocketEvent

    data class Read(val receipt: ReadReceiptWire) : ChatSocketEvent

    /** 🤖 one token of a reply that is still being written. */
    data class AiDelta(val conversationId: String, val messageId: String, val delta: String) : ChatSocketEvent

    data class AiDone(val conversationId: String, val message: ChatMessageWire?) : ChatSocketEvent

    data object Pong : ChatSocketEvent

    data class Failure(val frameId: String?, val code: String, val message: String) : ChatSocketEvent

    /** Connection-level, not a server frame. */
    data object Connected : ChatSocketEvent

    data class Disconnected(val reason: String) : ChatSocketEvent

    /** A frame this build does not understand. Kept so it can be logged rather than crash. */
    data class Unsupported(val type: String) : ChatSocketEvent
}
