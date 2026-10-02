package com.app.pustakam.feature.chat.domain.repository

import com.app.pustakam.core.drawing.live.DrawLiveEvent
import com.app.pustakam.core.drawing.live.DrawLiveRequest
import com.app.pustakam.core.drawing.live.DrawLiveRoom
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ILiveDrawingRepository {

    val rooms: StateFlow<Map<String, DrawLiveRoom>>

    val events: SharedFlow<DrawLiveEvent>

    fun join(roomId: String)

    fun leave(roomId: String)

    fun request(request: DrawLiveRequest)
}
