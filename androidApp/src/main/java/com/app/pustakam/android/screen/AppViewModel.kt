package com.app.pustakam.android.screen

import com.app.pustakam.android.screen.base.BaseViewModel
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.feature.auth.domain.usecase.AppUserCase
import com.app.pustakam.feature.chat.domain.usecase.SetChatForegroundUseCase
import com.app.pustakam.feature.chat.domain.usecase.StartChatUseCase
import com.app.pustakam.feature.chat.domain.usecase.StopChatUseCase
import org.koin.core.component.inject
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result

class AppViewModel : BaseViewModel() {
    private val appUserCase by inject<AppUserCase>()

    // 💬 31-Aug-2026: chat runs for the whole signed-in app, not just while its screen is open —
    //   otherwise a message only arrives once you go looking for it, and the bottom-bar badge
    //   never lights up while you are writing a note.
    private val startChat by inject<StartChatUseCase>()
    private val stopChat by inject<StopChatUseCase>()
    private val setChatForeground by inject<SetChatForegroundUseCase>()

    val authState = appUserCase.authState
    val isAuthenticated = appUserCase.isAuthenticated
    val unreadChats = startChat.totalUnread

    fun onAuthenticationChanged(isSignedIn: Boolean) {
        if (isSignedIn) startChat() else stopChat()
    }

    fun onForeground(isForeground: Boolean) = setChatForeground(isForeground)

    override fun onSuccess(taskCode: TaskCode, result: Result.Success<BaseResponse<*>>) {}

    override fun onFailure(taskCode: TaskCode, error: Error) {
        super.onFailure(taskCode, error)
    }

    override fun clearError() {}
}
