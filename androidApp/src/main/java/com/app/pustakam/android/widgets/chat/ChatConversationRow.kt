package com.app.pustakam.android.widgets.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.LoadImage
import com.app.pustakam.core.network.toAbsoluteMediaUrl
import com.app.pustakam.core.model.models.chat.ChatConversation
import com.app.pustakam.core.model.models.chat.ChatParticipant

private const val AVATAR_SIZE_DP = 46
private const val UNREAD_CAP = 99

/** One row of the inbox: who, the last thing said, when, and how much is unread. */
@Composable
fun ChatConversationRow(
    conversation: ChatConversation,
    modifier: Modifier = Modifier,
    isTyping: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatAvatar(
            label = conversation.displayTitle(),
            avatarUrl = conversation.avatarUrl(),
            isAssistant = conversation.isAssistant(),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.displayTitle(),
                style = typography.titleSmall,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // "typing…" replaces the preview rather than sitting beside it — one line, no jump
            if (isTyping) {
                Text(text = "typing…", style = typography.bodySmall, color = colorScheme.primary)
            } else {
                Text(
                    text = conversation.lastMessagePreview.ifBlank { "No messages yet" },
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = conversation.lastMessageAt.toInboxTimestamp(),
                style = typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
            if (conversation.unreadCount > 0) UnreadBadge(conversation.unreadCount)
        }
    }
}

/** A picked-from list of people to start a new chat with. */
@Composable
fun ChatPeerRow(participant: ChatParticipant, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatAvatar(label = participant.displayName(), avatarUrl = participant.avatarUrl)
        Column {
            Text(participant.displayName(), style = typography.titleSmall, color = colorScheme.onSurface)
            // 🆔 31-Aug-2026 — this printed the other person's EMAIL ADDRESS. It shows the handle now.
            participant.handle().takeIf { it.isNotBlank() }?.let {
                Text(it, style = typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** The picture, or the first letter when there is none. Never an empty grey circle. */
@Composable
fun ChatAvatar(
    label: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    isAssistant: Boolean = false,
) {
    val resolvedAvatarUrl = avatarUrl.toAbsoluteMediaUrl()
    Box(
        modifier = modifier
            .size(AVATAR_SIZE_DP.dp)
            .background(
                if (isAssistant) colorScheme.tertiaryContainer else colorScheme.secondaryContainer,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isAssistant -> Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = colorScheme.onTertiaryContainer,
            )

            // 🖼️ avatarUrl is stored RELATIVE (/media/avatar/<id>) — resolve it or nothing loads
            !resolvedAvatarUrl.isNullOrBlank() ->
                LoadImage(url = resolvedAvatarUrl, modifier = Modifier.size(AVATAR_SIZE_DP.dp))

            else -> Text(
                text = label.trim().firstOrNull()?.uppercase() ?: "?",
                style = typography.titleMedium,
                color = colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(min = 20.dp)
            .background(colorScheme.primary, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > UNREAD_CAP) "$UNREAD_CAP+" else "$count",
            style = typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
    }
}
