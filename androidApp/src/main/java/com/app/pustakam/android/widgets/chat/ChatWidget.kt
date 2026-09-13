package com.app.pustakam.android.widgets.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.screen.OnLifecycleEvent
import com.app.pustakam.android.screen.chat.ChatViewModel
import com.app.pustakam.feature.chat.domain.presentation.ChatState
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.LoadingUI
import com.app.pustakam.core.model.models.chat.ChatConnectionState
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

private const val LOAD_OLDER_TRIGGER_INDEX = 2

/**
 * 💬 THE reusable chat surface. Hand it a conversationId and it works anywhere — a bottom sheet, a
 * side panel beside a note, a dialog, or the full-screen chat screen, which is nothing more than
 * this widget with a header.
 *
 * It owns its own ViewModel keyed on the conversation, so two of these on one screen do not fight
 * over each other's state. Everything it renders comes from the shared ChatState.
 *
 * Media is deliberately NOT rendered here — [ChatAttachmentsView] hands attachments to the existing
 * note widgets, so an image in a chat looks and behaves exactly like an image in a note.
 */
@Composable
fun ChatWidget(
    conversationId: String,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(key = "chat-$conversationId"),
    showHeader: Boolean = false,
    showComposer: Boolean = true,
    emptyMessage: String = "No messages yet. Say something.",
    onOpenFullScreen: (() -> Unit)? = null,
    onAttach: () -> Unit = {},
    onOpenMedia: (NoteContentModel.MediaContent) -> Unit = {},
    onOpenDocument: (NoteContentModel.MediaContent) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) { viewModel.open(conversationId) }

    // Backgrounding drops the socket rather than holding a radio open; coming back reconnects
    // and marks whatever arrived meanwhile as read.
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> viewModel.onForeground(true)
            Lifecycle.Event.ON_PAUSE -> viewModel.onForeground(false)
            else -> Unit
        }
    }

    // A new message scrolls the thread down, the way every chat behaves
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    // Reaching the top asks for the previous page — guarded inside the ViewModel against repeats
    val shouldLoadOlder by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= LOAD_OLDER_TRIGGER_INDEX }
    }
    LaunchedEffect(shouldLoadOlder, state.messages.size) {
        if (shouldLoadOlder && state.messages.isNotEmpty()) viewModel.loadOlder()
    }

    Column(modifier = modifier.fillMaxSize().background(colorScheme.background)) {

        if (showHeader) {
            ChatWidgetHeader(
                title = state.title,
                subtitle = state.connection.subtitle(),
                avatarUrl = state.conversation?.avatarUrl(),
                isAssistant = state.isAssistantThread,
                onOpenFullScreen = onOpenFullScreen,
            )
            HorizontalDivider(color = colorScheme.outlineVariant)
        }

        if (state.showsConnectionBanner) ConnectionBanner(state.connection)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.messages.isEmpty() -> LoadingUI()

                state.isEmpty -> Text(
                    text = emptyMessage,
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (state.isLoadingOlder) {
                        item(key = "older-spinner") {
                            Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                                Text("Loading earlier messages…", style = typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    itemsIndexed(items = state.messages, key = { _, item -> item.id }) { index, message ->
                        // The date separator sits above the FIRST message of each day
                        val previous = state.messages.getOrNull(index - 1)
                        if (previous == null || previous.createdAt.toDayLabel() != message.createdAt.toDayLabel()) {
                            ChatDaySeparator(label = message.createdAt.toDayLabel())
                        }

                        ChatMessageBubble(
                            message = message,
                            isMine = state.isMine(message),
                            showSenderName = false,
                            onLongPress = { if (state.isMine(it)) viewModel.deleteMessage(it) },
                            onOpenMedia = onOpenMedia,
                            onOpenDocument = onOpenDocument,
                        )
                    }
                }
            }
        }

        TypingIndicator(names = state.typingNames())

        state.error?.let { message ->
            ChatErrorStrip(message = message, onDismiss = viewModel::clearError)
        }

        if (showComposer) {
            HorizontalDivider(color = colorScheme.outlineVariant)
            ChatComposer(
                draft = state.draft,
                canSend = state.canSend,
                attachments = state.pendingAttachments,
                placeholder = if (state.isAssistantThread) "Ask anything" else "Message",
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onAttach = onAttach,
                onRemoveAttachment = viewModel::onAttachmentRemoved,
            )
        }
    }
}

@Composable
private fun ChatWidgetHeader(
    title: String,
    subtitle: String?,
    avatarUrl: String?,
    isAssistant: Boolean,
    onOpenFullScreen: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChatAvatar(label = title, avatarUrl = avatarUrl, isAssistant = isAssistant, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = typography.labelSmall, color = colorScheme.onSurfaceVariant)
            }
        }
        onOpenFullScreen?.let {
            IconButton(onClick = it) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "Open full screen", tint = colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Honest about the socket. A chat that looks live while it is not is worse than one that says so. */
@Composable
private fun ConnectionBanner(state: ChatConnectionState) {
    Row(
        modifier = Modifier.fillMaxWidth().background(colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = colorScheme.onErrorContainer,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = if (state == ChatConnectionState.RECONNECTING) "Reconnecting…"
            else "Offline — messages will send when you are back",
            style = typography.labelSmall,
            color = colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ChatErrorStrip(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = typography.labelMedium, color = colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
        Text(
            text = "Dismiss",
            style = typography.labelMedium,
            color = colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 8.dp).clickable { onDismiss() },
        )
    }
}

/** Typing arrives as user ids; the conversation already carries the names to show instead. */
private fun ChatState.typingNames(): List<String> = typingUserIds.map { id ->
    conversation?.participantsInfo?.firstOrNull { it.id == id }?.displayName() ?: "Someone"
}

private fun ChatConnectionState.subtitle(): String? = when (this) {
    ChatConnectionState.CONNECTED -> "Online"
    ChatConnectionState.CONNECTING -> "Connecting…"
    ChatConnectionState.RECONNECTING -> "Reconnecting…"
    ChatConnectionState.DISCONNECTED -> "Offline"
}
