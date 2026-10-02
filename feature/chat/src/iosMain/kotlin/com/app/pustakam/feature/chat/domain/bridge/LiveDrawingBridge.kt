package com.app.pustakam.feature.chat.domain.bridge

import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.data.bridge.watch
import com.app.pustakam.core.drawing.live.DrawLiveEvent
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.core.drawing.live.DrawLiveRoom
import com.app.pustakam.feature.chat.domain.usecase.JoinLiveRoomUseCase
import com.app.pustakam.feature.chat.domain.usecase.LeaveLiveRoomUseCase
import com.app.pustakam.feature.chat.domain.usecase.SendLiveRequestUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LiveDrawingBridge : KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val joinRoom: JoinLiveRoomUseCase by inject()
    private val leaveRoom: LeaveLiveRoomUseCase by inject()
    private val sendRequest: SendLiveRequestUseCase by inject()

    fun observeRoom(roomId: String, onChange: (DrawLiveRoom?) -> Unit): Closeable =
        joinRoom.rooms.map { it[roomId] }.distinctUntilChanged().watch(scope) { onChange(it) }

    fun observeEvents(roomId: String, onEvent: (DrawLiveEvent) -> Unit): Closeable =
        joinRoom.events.filter { it.roomId == roomId }.watch(scope) { onEvent(it) }

    fun join(roomId: String) = joinRoom(roomId)

    fun leave(roomId: String) = leaveRoom(roomId)

    fun send(request: DrawLiveRequest) = sendRequest(request)

    fun dispose() = scope.cancel()
}
