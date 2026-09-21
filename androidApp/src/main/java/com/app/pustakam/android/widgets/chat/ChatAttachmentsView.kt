package com.app.pustakam.android.widgets.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
) {
    val medias = message.mediaContents()
    if (medias.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
