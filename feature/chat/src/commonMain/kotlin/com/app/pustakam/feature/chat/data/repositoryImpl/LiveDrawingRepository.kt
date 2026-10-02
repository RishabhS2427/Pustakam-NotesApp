package com.app.pustakam.feature.chat.data.repositoryImpl

import com.app.pustakam.core.common.coroutines.provideDispatcher
import com.app.pustakam.core.drawing.live.DrawLiveEvent
import com.app.pustakam.core.drawing.live.DrawLiveHub
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.core.drawing.live.DrawLiveRoom
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.feature.chat.domain.repository.IChatSocketRepository
import com.app.pustakam.feature.chat.domain.repository.ILiveDrawingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val LIVE_EVENT_BUFFER = 128

internal class LiveDrawingRepository : ILiveDrawingRepository, KoinComponent {

    private val socket by inject<IChatSocketRepository>()

    private val scope = CoroutineScope(SupervisorJob() + provideDispatcher().io)

    private val work = Channel<() -> Unit>(Channel.UNLIMITED)

    private val hub = DrawLiveHub { frame -> socket.sendFrame(frame) }

    private val _rooms = MutableStateFlow<Map<String, DrawLiveRoom>>(emptyMap())
    override val rooms: StateFlow<Map<String, DrawLiveRoom>> = _rooms.asStateFlow()

    private val _events = MutableSharedFlow<DrawLiveEvent>(replay = 0, extraBufferCapacity = LIVE_EVENT_BUFFER)
    override val events: SharedFlow<DrawLiveEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            for (job in work) {
                job()
                _rooms.value = hub.rooms()
            }
        }
        scope.launch {
            socket.frames.collect { raw -> enqueue { hub.onFrame(raw)?.let { _events.tryEmit(it) } } }
        }
        scope.launch {
            socket.connectionState.collect { state ->
                when (state) {
                    ChatConnectionState.CONNECTED -> enqueue { hub.onConnected() }
                    ChatConnectionState.DISCONNECTED -> enqueue { hub.onDisconnected() }
                    else -> Unit
                }
            }
        }
    }

    override fun join(roomId: String) {
        socket.connect()
        enqueue { hub.join(roomId) }
    }

    override fun leave(roomId: String) = enqueue { hub.leave(roomId) }

    override fun request(request: DrawLiveRequest) = enqueue { hub.request(request) }

    private fun enqueue(job: () -> Unit) {
        work.trySend(job)
    }
}
