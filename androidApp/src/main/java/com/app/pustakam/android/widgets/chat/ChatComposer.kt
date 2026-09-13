package com.app.pustakam.android.widgets.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.POutLinedTextFieldColors
import com.app.pustakam.core.model.models.chat.ChatAttachment

private const val COMPOSER_MAX_HEIGHT_DP = 140

/**
 * 💬 The message box. Send is disabled rather than hidden when there is nothing to send, so the
 * button never moves under the user's thumb.
 */
@Composable
fun ChatComposer(
    draft: String,
    canSend: Boolean,
    modifier: Modifier = Modifier,
    attachments: List<ChatAttachment> = emptyList(),
    placeholder: String = "Message",
    showAttachButton: Boolean = true,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (attachments.isNotEmpty()) {
            ComposerAttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showAttachButton) {
                IconButton(onClick = onAttach) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Attach a file",
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f).heightIn(max = COMPOSER_MAX_HEIGHT_DP.dp),
                placeholder = { Text(placeholder, style = typography.bodyMedium) },
                textStyle = typography.bodyMedium,
                colors = POutLinedTextFieldColors(),
                shape = RoundedCornerShape(22.dp),
                maxLines = 5,
            )

            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSend) colorScheme.primary else colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What is queued to go with the next message, each removable before it is sent. */
@Composable
private fun ComposerAttachmentStrip(
    attachments: List<ChatAttachment>,
    onRemove: (ChatAttachment) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(attachments) { attachment ->
            Row(
                modifier = Modifier
                    .background(colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.title.ifBlank { attachment.contentType.name },
                    style = typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemove(attachment) }, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove attachment",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
