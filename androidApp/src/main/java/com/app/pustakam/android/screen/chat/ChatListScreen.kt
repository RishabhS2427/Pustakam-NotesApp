package com.app.pustakam.android.screen.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.LoadingUI
import com.app.pustakam.android.widgets.POutLinedTextFieldColors
import com.app.pustakam.android.widgets.chat.ChatConversationRow
import com.app.pustakam.android.widgets.chat.ChatPeerRow
import com.app.pustakam.core.model.models.chat.ChatParticipant

/**
 * 💬 The inbox — the bottom-bar destination. Tapping a row opens the full-screen thread; the FAB
 * opens the people picker, which also offers a fresh conversation with the assistant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openedId by viewModel.openedConversationId.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) { viewModel.load() }

    // Opening a new conversation navigates once, then the signal is consumed so reopening the
    // inbox does not jump straight back into that thread.
    LaunchedEffect(openedId) {
        openedId?.let {
            onOpenConversation(it)
            viewModel.consumeOpenedConversation()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.conversations.isEmpty() -> LoadingUI()

                state.isEmpty -> EmptyInbox(onStartAssistant = viewModel::startAssistantChat)

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = state.visibleConversations, key = { it.id }) { conversation ->
                        ChatConversationRow(
                            conversation = conversation,
                            onClick = { onOpenConversation(conversation.id) },
                        )
                        HorizontalDivider(color = colorScheme.outlineVariant)
                    }
                }
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = typography.labelMedium,
                color = colorScheme.onErrorContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        ExtendedFloatingActionButton(
            onClick = viewModel::openPeerPicker,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            text = { Text("New chat") },
        )
    }

    if (state.isPickingPeer) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closePeerPicker,
            sheetState = sheetState,
            containerColor = colorScheme.surface,
        ) {
            PeerPickerSheet(
                query = state.query,
                peers = state.visiblePeers,
                isSearching = state.isSearchingPeers,
                isIdle = state.isPeerSearchIdle,
                onQueryChange = viewModel::onQueryChange,
                onPickPeer = viewModel::startDirectChat,
                onPickAssistant = viewModel::startAssistantChat,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerPickerSheet(
    query: String,
    peers: List<ChatParticipant>,
    isSearching: Boolean,
    isIdle: Boolean,
    onQueryChange: (String) -> Unit,
    onPickPeer: (String) -> Unit,
    onPickAssistant: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = "Start a conversation",
            style = typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // 🤖 the assistant sits at the top of the picker, not behind a separate entry point
        AssistantPickerRow(onClick = onPickAssistant)

        HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            // 🆔 usernames only — there is no browsable directory of everyone any more
            placeholder = { Text("Search by username", style = typography.bodyMedium) },
            prefix = { Text("@", style = typography.bodyMedium, color = colorScheme.onSurfaceVariant) },
            colors = POutLinedTextFieldColors(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Search,
            ),
        )

        when {
            isIdle -> PickerHint("Type a username to find someone")
            isSearching && peers.isEmpty() -> PickerHint("Searching…")
            peers.isEmpty() -> PickerHint("No one found with that username")
            else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = peers, key = { it.id }) { participant ->
                    ChatPeerRow(participant = participant, onClick = { onPickPeer(participant.id) })
                }
            }
        }
    }
}

@Composable
private fun PickerHint(text: String) {
    Text(
        text = text,
        style = typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@Composable
private fun AssistantPickerRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colorScheme.primary)
        Column {
            Text("Pustakam AI", style = typography.titleSmall)
            Text("Ask the assistant anything", style = typography.bodySmall, color = colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyInbox(onStartAssistant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No conversations yet", style = typography.titleMedium, color = colorScheme.onSurface)
        Text(
            text = "Start one with someone, or ask the assistant.",
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        ExtendedFloatingActionButton(
            onClick = onStartAssistant,
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
            text = { Text("Chat with Pustakam AI") },
        )
    }
}
