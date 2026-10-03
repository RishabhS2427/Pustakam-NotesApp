package com.app.pustakam.android.screen.notes.single

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.MyApplicationTheme
import com.app.pustakam.android.widgets.LoadImage
import com.app.pustakam.core.model.models.response.notes.NoteSummary
import com.app.pustakam.core.common.extensions.toLocalFormat

private const val SHARED_PREFIX = "Shared · "

@Composable
fun NoteBookCardView(modifier: Modifier = Modifier, summary: NoteSummary,
                     onClick: () -> Unit = {}) {
    Card(shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface,
        ),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp,
            hoveredElevation = 4.dp, focusedElevation = 4.dp,  pressedElevation = 2.dp),
        modifier = modifier.fillMaxWidth(0.5f).clickable { onClick() }
        .heightIn(min = 100.dp, max = 300.dp).wrapContentHeight().padding(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.End) {
                // 🎨 20-Jul-2026 — meta date as tertiary text (spec §2.2 text-3), no heavy gradient chip
                Text(
                    (if (summary.shared) SHARED_PREFIX else "") + summary.updatedAt?.toLocalFormat(showTime = false).toString(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colorScheme.onSurfaceVariant
                    ), modifier = Modifier.padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 4.dp)
                )
                // 🎨 20-Jul-2026 — serif card title, ink text on ivory (spec §3 card title / §2.2 text)
                Text(
                    if (!summary.title.isNullOrEmpty()) summary.title.toString() else "Title?",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = colorScheme.onSurface
                    ), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )

            }
            // Text snippet — only when the note actually has text.
            if (!summary.snippet.isNullOrBlank()) {
                Column{
                Text(
                    summary.snippet!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colorScheme.onSurfaceVariant // 🎨 secondary text on ivory (spec §2.2)
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                MediaCountBadges(summary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            }
            else {
                // Media/doc-only note: describe it with count badges instead of an empty card.
                MediaCountBadges(summary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            // Thumbnail strip for the first visual media (image/video/gif).
            if (!summary.thumbnailPath.isNullOrEmpty()) {
                LoadImage(
                    url = summary.thumbnailPath!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Box(Modifier.height(8.dp))
        }
    }
}

// 🔧 15-Jul-2026 Summary query: compact "what's inside" row for notes without text.
@Composable
private fun MediaCountBadges(summary: NoteSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CountBadge(Icons.Filled.Image, summary.imageCount)
        CountBadge(Icons.Filled.Videocam, summary.videoCount)
        CountBadge(Icons.Filled.Mic, summary.audioCount)
        CountBadge(Icons.Filled.Description, summary.docCount)
    }
}

@Composable
private fun CountBadge(icon: ImageVector, count: Int) {
    if (count <= 0) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colorScheme.onSurfaceVariant, // 🎨 secondary on ivory
            modifier = Modifier.size(14.dp))
        Text(
            if (count > 1000 ) "${count/1000}k" else "$count",
            style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
        )
    }
}

@Preview("default")
@Preview("dark theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview("large font", fontScale = 2f)

@Composable
private fun NotesPreview() {
    MyApplicationTheme {
        val summary = NoteSummary(
            id = "1", title = "Hare Rama Hare Rama", updatedAt = "18/03/2025",
            createdAt = "", categoryId = "",
            snippet = "", imageCount = 3, audioCount = 1, docCount = 2
        )
        NoteBookCardView(summary = summary)
    }
}
