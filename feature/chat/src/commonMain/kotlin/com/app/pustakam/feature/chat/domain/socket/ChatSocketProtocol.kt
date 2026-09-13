package com.app.pustakam.feature.chat.domain.socket

import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.model.models.chat.SocketAckPayload
import com.app.pustakam.core.model.models.chat.SocketAiDeltaPayload
import com.app.pustakam.core.model.models.chat.SocketAiDonePayload
import com.app.pustakam.core.model.models.chat.SocketErrorPayload
import com.app.pustakam.core.model.models.chat.SocketReadPayload
import com.app.pustakam.core.model.models.chat.SocketReadyPayload
import com.app.pustakam.core.model.models.chat.SocketSendPayload
import com.app.pustakam.core.model.models.chat.SocketTypingPayload
import com.app.pustakam.core.model.models.chat.ChatMessageWire
import com.app.pustakam.core.model.models.chat.ReadReceiptWire
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 💬 The wire format, in one place: `{ "t": <type>, "id": <correlation>, "d": <payload> }`.
 *
 * PustakmServer/realtime/wsProtocol.js is the other half of this contract. Change one without the
 * other and the chat stops delivering with nothing in the logs to say why — so treat the two files
 * as a pair. Everything here is pure, which is what makes it unit-testable without a socket.
 */
object ChatSocketProtocol {

    // Frame types. Same strings as the server's ChatEvent.
    const val READY = "ready"
    const val SEND = "send"
    const val MESSAGE = "message"
    const val ACK = "ack"
    const val TYPING = "typing"
    const val READ = "read"
    const val PRESENCE = "presence"
    const val AI_DELTA = "ai_delta"
    const val AI_DONE = "ai_done"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"

    const val CODE_MALFORMED = "BAD_ENVELOPE"

    @Serializable
    private data class Envelope(
        val t: String,
        val id: String? = null,
        val d: JsonElement? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    // ── outbound ──────────────────────────────────────────────────────────────

    fun encodeSend(frameId: String, payload: SocketSendPayload): String =
        encode(SEND, frameId, json.encodeToJsonElement(SocketSendPayload.serializer(), payload))

    fun encodeTyping(payload: SocketTypingPayload): String =
        encode(TYPING, null, json.encodeToJsonElement(SocketTypingPayload.serializer(), payload))

    fun encodeRead(frameId: String, payload: SocketReadPayload): String =
        encode(READ, frameId, json.encodeToJsonElement(SocketReadPayload.serializer(), payload))

    fun encodePing(frameId: String): String = encode(PING, frameId, JsonObject(emptyMap()))

    private fun encode(type: String, frameId: String?, data: JsonElement): String =
        json.encodeToString(Envelope.serializer(), Envelope(t = type, id = frameId, d = data))

    // ── inbound ───────────────────────────────────────────────────────────────

    /**
     * Never throws. A frame this build cannot read becomes [ChatSocketEvent.Unsupported] or a
     * [ChatSocketEvent.Failure]; one bad frame must not take the connection down with it.
     */
    fun decode(raw: String): ChatSocketEvent {
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), raw)
        } catch (e: Throwable) {
            log_d("ChatSocket", "undecodable frame: $e")
            return ChatSocketEvent.Failure(null, CODE_MALFORMED, "Unreadable frame")
        }

        val data = envelope.d
        return try {
            when (envelope.t) {
                READY -> decodeAs(data, SocketReadyPayload.serializer())
                    ?.let { ChatSocketEvent.Ready(it.userId, it.serverTime) }

                MESSAGE -> decodeAs(data, ChatMessageWire.serializer())
                    ?.let { ChatSocketEvent.MessageReceived(it) }

                ACK -> decodeAck(envelope.id, data)

                TYPING -> decodeAs(data, SocketTypingPayload.serializer())
                    ?.let { ChatSocketEvent.Typing(it.conversationId, it.userId, it.isTyping) }

                READ -> decodeAs(data, ReadReceiptWire.serializer())?.let { ChatSocketEvent.Read(it) }

                AI_DELTA -> decodeAs(data, SocketAiDeltaPayload.serializer())
                    ?.let { ChatSocketEvent.AiDelta(it.conversationId, it.messageId, it.delta) }

                AI_DONE -> decodeAs(data, SocketAiDonePayload.serializer())
                    ?.let { ChatSocketEvent.AiDone(it.conversationId, it.message) }

                PONG -> ChatSocketEvent.Pong

                ERROR -> decodeAs(data, SocketErrorPayload.serializer())
                    ?.let { ChatSocketEvent.Failure(envelope.id, it.code, it.message) }

                // PRESENCE carries a conversation row; the list refreshes itself, so it is noise here
                else -> ChatSocketEvent.Unsupported(envelope.t)
            } ?: ChatSocketEvent.Failure(envelope.id, CODE_MALFORMED, "Missing payload for \"${envelope.t}\"")
        } catch (e: Throwable) {
            log_d("ChatSocket", "unreadable payload for ${envelope.t}: $e")
            return ChatSocketEvent.Failure(envelope.id, CODE_MALFORMED, "Unreadable payload")
        }
    }

    /**
     * An ack that carries a read receipt rather than a message is the answer to a `read` frame —
     * the server uses one ack type for both, so the payload decides which this is.
     */
    private fun decodeAck(frameId: String?, data: JsonElement?): ChatSocketEvent? {
        if (data == null) return null
        decodeAs(data, SocketAckPayload.serializer())?.let { payload ->
            if (payload.message != null || payload.clientId != null) {
                return ChatSocketEvent.Ack(frameId, payload.clientId, payload.message, payload.duplicate)
            }
        }
        return decodeAs(data, ReadReceiptWire.serializer())?.let { ChatSocketEvent.Read(it) }
            ?: ChatSocketEvent.Ack(frameId, null, null, false)
    }

    /**
     * Takes the serializer explicitly rather than reifying T. Both overloads it resolves to are
     * MEMBERS of Json, so unlike the reified `decodeFromJsonElement<T>()` there is no import for an
     * "optimize imports" pass to delete — which is how this file broke twice.
     */
    private fun <T> decodeAs(data: JsonElement?, deserializer: DeserializationStrategy<T>): T? {
        if (data == null) return null
        return try {
            json.decodeFromJsonElement(deserializer, data)
        } catch (e: Throwable) {
            log_d("ChatSocket", "payload did not fit ${deserializer.descriptor.serialName}: $e")
            null
        }
    }
}
