package com.app.pustakam.android.widgets.document

// 🔧 18-Jul-2026: NEW FEATURE (file import) — inline card for imported document/file content
//   (PDF/DOCX/EPUB/TXT/MD/OTHER). Tap = open in the book reader; long-press = actions overlay.
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.media.MediaDownloadOverlay
import com.app.pustakam.core.media.format.MediaSizeFormatter
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.common.util.ContentType

// 🔧 18-Jul-2026: type → glyph for the card leading icon
fun iconForContentType(type: ContentType): ImageVector = when (type) {
    ContentType.PDF -> Icons.Filled.PictureAsPdf
    ContentType.DOCX -> Icons.Filled.Description
    ContentType.EPUB -> Icons.AutoMirrored.Filled.MenuBook
    ContentType.TXT ->Icons.AutoMirrored.Filled.TextSnippet
    ContentType.MD -> Icons.AutoMirrored.Filled.TextSnippet
    ContentType.IMAGE -> Icons.Filled.Image
    ContentType.GIF -> Icons.Filled.GifBox
    ContentType.VIDEO -> Icons.Filled.VideoFile
    ContentType.AUDIO -> Icons.Filled.AudioFile
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

// 📏 20-Sep-2026: one size-wording rule, shared with the download bar and with iOS
fun readableSize(bytes: Long): String = if (bytes <= 0) "" else MediaSizeFormatter.formatBytes(bytes)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentFileCard(
    media: NoteContentModel.MediaContent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onShowActions: (Boolean) -> Unit = {},
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                // 🔧 18-Jul-2026: tap opens in book reader; long-press reveals save/share/delete
                .combinedClickable(onClick = onClick, onLongClick = { onShowActions(true) })
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconForContentType(media.type),
                        contentDescription = media.type.name,
                        tint = colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        media.title.ifBlank { media.localPath?.substringAfterLast('/') ?: "File" },
                        style = typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOf(media.type.name, readableSize(media.sizeBytes))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    Text(subtitle, style = typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
            }
        }
        // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
        MediaDownloadOverlay(media, Modifier.align(Alignment.BottomCenter))
        overlay()
    }
}
