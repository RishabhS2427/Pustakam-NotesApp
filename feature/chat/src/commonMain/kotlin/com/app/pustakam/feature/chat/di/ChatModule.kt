package com.app.pustakam.feature.chat.di

import com.app.pustakam.feature.chat.data.repositoryImpl.ChatRepository
import com.app.pustakam.feature.chat.data.repositoryImpl.ChatSocketRepository
import com.app.pustakam.feature.chat.domain.repository.IChatRepository
import com.app.pustakam.feature.chat.domain.repository.IChatSocketRepository
import com.app.pustakam.feature.chat.domain.usecase.DeleteChatMessageUseCase
import com.app.pustakam.feature.chat.domain.usecase.DeleteConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.FlushChatOutboxUseCase
import com.app.pustakam.feature.chat.domain.usecase.GetChatPeersUseCase
import com.app.pustakam.feature.chat.domain.usecase.LoadChatHistoryUseCase
import com.app.pustakam.feature.chat.domain.usecase.MarkConversationReadUseCase
import com.app.pustakam.feature.chat.domain.usecase.ObserveConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.OpenConversationUseCase
import com.app.pustakam.feature.chat.domain.usecase.RefreshConversationsUseCase
import com.app.pustakam.feature.chat.domain.usecase.SendChatMessageUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetChatForegroundUseCase
import com.app.pustakam.feature.chat.domain.usecase.SetTypingUseCase
import com.app.pustakam.feature.chat.domain.usecase.StartChatUseCase
import com.app.pustakam.feature.chat.domain.usecase.StopChatUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

fun chatModule(): Module = module {
    // 🔌 single, not factory: a second ChatSocketRepository opens a SECOND websocket on the same
    //   account, and the server then delivers every message twice.
    single<IChatSocketRepository> { ChatSocketRepository() }
    // 💬 single: it owns the conversation, message and typing flows every screen collects
    single<IChatRepository> { ChatRepository() }

    factory<StartChatUseCase> { StartChatUseCase() }
    factory<StopChatUseCase> { StopChatUseCase() }
    factory<SetChatForegroundUseCase> { SetChatForegroundUseCase() }
    factory<FlushChatOutboxUseCase> { FlushChatOutboxUseCase() }
    factory<RefreshConversationsUseCase> { RefreshConversationsUseCase() }
    factory<OpenConversationUseCase> { OpenConversationUseCase() }
    factory<DeleteConversationUseCase> { DeleteConversationUseCase() }
    factory<GetChatPeersUseCase> { GetChatPeersUseCase() }
    factory<ObserveConversationUseCase> { ObserveConversationUseCase() }
    factory<LoadChatHistoryUseCase> { LoadChatHistoryUseCase() }
    factory<SendChatMessageUseCase> { SendChatMessageUseCase() }
    factory<DeleteChatMessageUseCase> { DeleteChatMessageUseCase() }
    factory<MarkConversationReadUseCase> { MarkConversationReadUseCase() }
    factory<SetTypingUseCase> { SetTypingUseCase() }
}
