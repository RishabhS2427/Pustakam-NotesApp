package com.app.pustakam.android.widgets.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.screen.share.ShareNoteViewModel
import com.app.pustakam.android.theme.typography
import com.app.pustakam.core.model.models.share.NoteRole
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.NoteShareMember
import com.app.pustakam.feature.notes.domain.share.ShareSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteSheet(
    noteId: String,
    onDismiss: () -> Unit,
    viewModel: ShareNoteViewModel = viewModel(key = "share-$noteId")
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(noteId) { viewModel.open(noteId) }
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
                Text("Share a copy", style = typography.titleMedium)
                Text(
                    "People get their own shared version of this note.",
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
                    label = { Text("Search people") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(state.results, key = { "result-${it.userId}" }) { person ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.pick(person) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(person.name, style = typography.titleSmall)
                    if (person.handle.isNotEmpty()) {
                        Text(person.handle, style = typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.picked.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.picked.forEach { person ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.unpick(person.userId) },
                                label = { Text(person.name) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove") }
                            )
                        }
                    }
                }
            }
            item {
                Button(onClick = viewModel::send, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Sharing…" else "Share")
                }
            }
            state.error?.let { message ->
                item {
                    Text(message, color = colorScheme.error, style = typography.bodySmall, modifier = Modifier.clickable { viewModel.clearError() })
                }
            }
            if (state.sent) {
                item { Text("Shared. They will see it in their notes.", style = typography.bodySmall, color = colorScheme.primary) }
            }
            state.shares.forEach { share ->
                item(key = "share-${share.shareId}") {
                    ShareGroup(
                        share = share,
                        onRole = { userId, role -> viewModel.changeRole(share.shareId, userId, role) },
                        onRemove = { userId -> viewModel.remove(share.shareId, userId) },
                        onStop = { viewModel.stop(share.shareId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareGroup(
    share: NoteShare,
    onRole: (String, NoteRole) -> Unit,
    onRemove: (String) -> Unit,
    onStop: () -> Unit
) {
    val manage = ShareSheet.canManage(share)
    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 0.dp)) {
        HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Shared copy · ${share.members.size}",
                style = typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (manage) TextButton(onClick = onStop) { Text("Stop sharing", color = colorScheme.error) }
        }
        share.members.forEach { member ->
            ShareMemberRow(member = member, manage = manage, onRole = { onRole(member.userId, it) }, onRemove = { onRemove(member.userId) })
        }
    }
}

@Composable
private fun ShareMemberRow(
    member: NoteShareMember,
    manage: Boolean,
    onRole: (NoteRole) -> Unit,
    onRemove: () -> Unit
) {
    var picking by remember(member.userId) { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(member.displayName(), style = typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { picking = true }, enabled = manage) { Text(ShareSheet.label(member.access)) }
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                NoteRole.entries.forEach { role ->
                    DropdownMenuItem(text = { Text(ShareSheet.label(role)) }, onClick = {
                        picking = false
                        onRole(role)
                    })
                }
            }
        }
        if (manage) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.PersonRemove, contentDescription = "Remove access", tint = colorScheme.error)
            }
        }
    }
}
