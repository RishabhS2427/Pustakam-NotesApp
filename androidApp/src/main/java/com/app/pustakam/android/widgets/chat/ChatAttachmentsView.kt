package com.app.pustakam.android.widgets.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.core.model.models.chat.ChatAttachment
import com.app.pustakam.feature.notes.domain.share.ChatShareSheet
import com.app.pustakam.android.widgets.audio.AudioPlayerUIState
import com.app.pustakam.android.widgets.document.DocumentFileCard
import com.app.pustakam.android.widgets.image.ImageCard
import com.app.pustakam.android.widgets.video.VideoCard
import com.app.pustakam.core.common.util.isDoc
import com.app.pustakam.core.common.util.isImage
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.model.models.chat.ChatMessage
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.getMediaUrl

/**
 * 🧩 DRY: chat has NO media widgets of its own. An attachment becomes a NoteContentModel.MediaContent
 * and the note cards render it — the same ImageCard, VideoCard, AudioPlayerUIState and
 * DocumentFileCard the editor and the reader already use. A fix to any of them fixes chat too.
 */
@Composable
fun ChatAttachmentsView(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onOpenMedia: (NoteContentModel.MediaContent) -> Unit = {},
    onOpenDocument: (NoteContentModel.MediaContent) -> Unit = {},
    onOpenNote: (String) -> Unit = {},
) {
    val medias = message.mediaContents()
    val notes = message.sharedNotes()
    if (medias.isEmpty() && notes.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        notes.forEach { card -> SharedNoteCard(card = card, onOpen = onOpenNote) }
        medias.forEach { media ->
            when {
                media.type.isImage() -> ImageCard(
                    imageUrl = media.getMediaUrl(),
                    media = media,
                    onClick = { onOpenMedia(media) },
                )

                media.type == ContentType.VIDEO -> VideoCard(
                    contentVideo = media,
                    onClick = { onOpenMedia(media) },
                    widthFraction = 1f,
                )

                media.type == ContentType.AUDIO -> AudioPlayerUIState(noteContentModel = media)

                media.type.isDoc() -> DocumentFileCard(
                    media = media,
                    onClick = { onOpenDocument(media) },
                )

                else -> DocumentFileCard(media = media, onClick = { onOpenDocument(media) })
            }
        }
    }
}

@Composable
private fun SharedNoteCard(card: ChatAttachment, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable { card.sharedNoteId()?.let(onOpen) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Description, contentDescription = null, tint = colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title.ifBlank { ChatShareSheet.UNTITLED },
                style = typography.titleSmall,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = ChatShareSheet.OPEN_NOTE, style = typography.labelSmall, color = colorScheme.primary)
        }
    }
}
