package com.app.pustakam.android.widgets.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.core.model.models.chat.AiReplyState
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.chat.ChatMessageStatus
import com.app.pustakam.core.model.models.response.notes.NoteContentModel

private const val BUBBLE_WIDTH_FRACTION = 0.82f

/**
 * 💬 One message. Mine on the right in the accent colour, everyone else's on the left; the
 * assistant gets its own tint and a sparkle so an AI answer is never mistaken for a person's.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    showSenderName: Boolean = false,
    senderName: String = "",
    onLongPress: (ChatMessage) -> Unit = {},
    onOpenMedia: (NoteContentModel.MediaContent) -> Unit = {},
    onOpenDocument: (NoteContentModel.MediaContent) -> Unit = {},
) {
    val isAssistant = message.isFromAssistant()
    val bubbleColor = when {
        isMine -> colorScheme.primaryContainer
        isAssistant -> colorScheme.tertiaryContainer
        else -> colorScheme.surfaceVariant
    }
    val textColor = when {
        isMine -> colorScheme.onPrimaryContainer
        isAssistant -> colorScheme.onTertiaryContainer
        else -> colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LocalBubbleMaxWidth())
                .background(bubbleColor, bubbleShapeFor(isMine))
                .combinedClickable(onClick = {}, onLongClick = { onLongPress(message) })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (showSenderName && !isMine && senderName.isNotBlank()) {
                Text(
                    text = senderName,
                    style = typography.labelMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

            ChatAttachmentsView(
                message = message,
                onOpenMedia = onOpenMedia,
                onOpenDocument = onOpenDocument,
            )

            if (message.text.isNotBlank()) {
                Text(text = message.text, style = typography.bodyMedium, color = textColor)
            }

            // 🤖 an answer that is still being written shows dots rather than an empty bubble
            if (message.isStreaming() && message.text.isBlank()) {
                TypingDots(tint = textColor)
            }

            Row(
                modifier = Modifier.padding(top = 3.dp).align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isAssistant) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = message.createdAt.toClockTime(),
                    style = typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                )
                // Only my own messages carry ticks — a tick on someone else's is meaningless
                if (isMine) StatusTick(message.status, textColor)
            }

            if (message.aiState == AiReplyState.FAILED && message.aiError != null) {
                Text(
                    text = message.aiError.orEmpty(),
                    style = typography.labelSmall,
                    color = colorScheme.error,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun StatusTick(status: ChatMessageStatus, tint: Color) {
    val icon: ImageVector = when (status) {
        ChatMessageStatus.SENDING -> Icons.Filled.Schedule
        ChatMessageStatus.SENT -> Icons.Filled.Done
        ChatMessageStatus.DELIVERED, ChatMessageStatus.READ -> Icons.Filled.DoneAll
        ChatMessageStatus.FAILED -> Icons.Filled.ErrorOutline
    }
    val color = when (status) {
        ChatMessageStatus.READ -> colorScheme.primary
        ChatMessageStatus.FAILED -> colorScheme.error
        else -> tint.copy(alpha = 0.7f)
    }
    Icon(imageVector = icon, contentDescription = status.name, tint = color, modifier = Modifier.size(13.dp))
}

@Composable
private fun bubbleShapeFor(isMine: Boolean) = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = if (isMine) 16.dp else 4.dp,
    bottomEnd = if (isMine) 4.dp else 16.dp,
)

@Composable
private fun LocalBubbleMaxWidth() =
    (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * BUBBLE_WIDTH_FRACTION).dp

/** A day separator, so a thread read weeks later still says when things were said. */
@Composable
fun ChatDaySeparator(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
