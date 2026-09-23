package com.app.pustakam.android.widgets.document
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.app.pustakam.android.widgets.media.MediaDownloadOverlay
import androidx.compose.ui.draw.clipToBounds   // 🔧 20-Jul-2026: keep zoom inside the card
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration   // 🔧 20-Jul-2026: device-relative size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.extension.actionIconButtonBackground
import com.app.pustakam.android.screen.bookUIView.BookPageContent
import com.app.pustakam.android.screen.notebookReader.BookPage

import com.app.pustakam.android.screen.notebookReader.BookPageFactory
import com.app.pustakam.android.screen.notebookReader.BookPager
import com.app.pustakam.android.theme.actionIconTintColor
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.bookwidget.BookLoadingAnimation
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NotebookPaper = Color(0xFFFAF3E3)
private val NotebookInk = Color(0xFF3E2F1C)
private val NotebookCover = Color(0xFF4A3527)
private val SpiralMetal = Color(0xFF8D8578)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineBookFileWidget(
    media: NoteContentModel.MediaContent,
    modifier: Modifier = Modifier,
    onOpenFull: () -> Unit = {},
    onShowActions: (Boolean) -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    // 🧱 24-Sep-2026 — the master canvas sizes and spaces the card itself; the defaults keep the note editor as it was
    widthFraction: Float = 0.75f,
    fixedHeight: Dp? = null,
    outerPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var pages by remember(media.id) { mutableStateOf<List<BookPage>>(emptyList()) }
    var building by remember(media.id) { mutableStateOf(true) }
    LaunchedEffect(media.id, media.updatedAt) {
        building = true
        pages = withContext(Dispatchers.IO) { BookPageFactory.buildForContent(media) }
        building = false
    }
    val resumePage = remember(media.id, pages.size) {
        if (media.hasReadingProgress() && pages.isNotEmpty())
            media.progressPage.coerceIn(0, pages.size - 1) else 0
    }
    val hideJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var currentPage by remember(media.id) { mutableIntStateOf(0) }

    LaunchedEffect(resumePage) { currentPage = resumePage }
    val cornerShape = RoundedCornerShape(8.dp)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val cardHeight = (screenHeightDp * 0.42f).dp.coerceIn(260.dp, 460.dp)
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .padding(outerPadding)
            .height(fixedHeight ?: cardHeight)
            .background(NotebookCover,cornerShape )
    ) {
        Row(Modifier.fillMaxSize().padding(2.dp)) {
            SpiralBinding()
            Box(
                Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .background(NotebookPaper, cornerShape)

            ) {
                Column(Modifier.fillMaxSize()) {
                    // header — file name; long-press reveals actions, expand opens the full book
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 4.dp)
                    ) {
                        Icon(
                            iconForContentType(media.type), contentDescription = media.type.name,
                            tint = NotebookCover, modifier = Modifier.size(16.dp)
                        )
                        Text(
                            media.title.ifBlank { "File" },
                            style = typography.labelLarge.copy(fontFamily = FontFamily.Serif),
                            color = NotebookInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        IconButton(onClick = onOpenFull, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Filled.OpenInFull, contentDescription = "Open full book",
                                tint = NotebookCover, modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = { onShowActions(true)
                            hideJob.value?.cancel()
                            hideJob.value = scope.launch {
                                delay(2500)
                                onShowActions(false)
                            }}, modifier = Modifier.size(30.dp)) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options",
                                tint = NotebookCover,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NotebookInk.copy(alpha = .15f)))
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        when {
                            // Change this with page loader
                            building-> BookLoadingAnimation(
                                modifier = Modifier.align(Alignment.Center).size(28.dp)
                            )
                            pages.isNotEmpty() -> BookPager(
                                pageCount = pages.size,
                                initialPage = resumePage,   // 📖 25-Jul-2026: open at the last-read page
                                onPageChanged = { currentPage = it
                                    onPageChanged(it) },
                            ) { index -> BookPageContent(page = pages[index]) }
                        }
                    }
                    // 🔧 19-Jul-2026: "<selected>/<total>" instead of dots — e.g. 2/20
                    if (pages.isNotEmpty()) Text(
                        "${currentPage + 1}/${pages.size}",
                        style = typography.labelMedium, color = NotebookInk.copy(alpha = .65f),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 3.dp)
                    )
                }
            }
        }
        // 📥 20-Sep-2026 — the generic transfer bar; draws nothing once the bytes are here
        MediaDownloadOverlay(media, Modifier.align(Alignment.BottomCenter))
        overlay()
    }
}

@Composable
private fun SpiralBinding() {
    Canvas(Modifier.width(26.dp).fillMaxHeight().padding(vertical = 14.dp)) {
        val ringSpacing = 30.dp.toPx()
        val ringRadius = 7.dp.toPx()
        val centerX = size.width / 2f
        var y = ringRadius
        while (y + ringRadius < size.height) {
            // ring (metal loop) + punched hole highlight
            drawCircle(
                color = SpiralMetal, radius = ringRadius,
                center = Offset(centerX, y), style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = .35f), Color.Transparent),
                    center = Offset(centerX + ringRadius * .55f, y), radius = ringRadius * .8f
                ),
                radius = ringRadius * .55f,
                center = Offset(centerX + ringRadius * .55f, y)
            )
            y += ringSpacing
        }
    }
}
