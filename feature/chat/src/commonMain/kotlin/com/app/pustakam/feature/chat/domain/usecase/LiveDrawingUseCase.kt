package com.app.pustakam.feature.chat.domain.usecase

import com.app.pustakam.core.data.usecases.BaseUseCase
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.feature.chat.domain.repository.ILiveDrawingRepository
import org.koin.core.component.inject

abstract class LiveDrawingBaseUseCase : BaseUseCase() {
    protected val liveDrawing: ILiveDrawingRepository by inject()

    val rooms = liveDrawing.rooms
    val events = liveDrawing.events
}

class JoinLiveRoomUseCase : LiveDrawingBaseUseCase() {
    operator fun invoke(roomId: String) = liveDrawing.join(roomId)
}

class LeaveLiveRoomUseCase : LiveDrawingBaseUseCase() {
    operator fun invoke(roomId: String) = liveDrawing.leave(roomId)
}

class SendLiveRequestUseCase : LiveDrawingBaseUseCase() {
    operator fun invoke(request: DrawLiveRequest) = liveDrawing.request(request)
}
