package com.app.pustakam.android.widgets.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.screen.share.ChatShareViewModel
import com.app.pustakam.android.theme.typography
import com.app.pustakam.feature.notes.domain.share.ChatShareSheet
import com.app.pustakam.feature.notes.domain.share.ShareSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToChatSheet(
    conversationId: String,
    onDismiss: () -> Unit,
    viewModel: ChatShareViewModel = viewModel(key = "chat-share-$conversationId")
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(conversationId) { viewModel.open(conversationId) }
    LaunchedEffect(state.sent) { if (state.sent) onDismiss() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Share a note", style = typography.titleMedium)
                Text(
                    "Everyone in this chat gets their own shared version of the note.",
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.roles.forEach { role ->
                        FilterChip(
                            selected = state.role == role,
                            onClick = { viewModel.chooseRole(role) },
                            label = { Text(ShareSheet.label(role)) }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search your notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(state.notes, key = { "note-${it.id}" }) { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.pick(note.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        ChatShareSheet.title(note),
                        style = typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.pickedId == note.id) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Picked", tint = colorScheme.primary)
                    }
                }
            }
            item {
                Button(onClick = viewModel::send, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Sharing…" else "Share in chat")
                }
            }
            state.error?.let { message ->
                item {
                    Text(message, color = colorScheme.error, style = typography.bodySmall, modifier = Modifier.clickable { viewModel.clearError() })
                }
            }
        }
    }
}
