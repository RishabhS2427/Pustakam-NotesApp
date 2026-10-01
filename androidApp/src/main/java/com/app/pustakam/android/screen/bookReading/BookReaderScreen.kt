package com.app.pustakam.android.screen.bookReading

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.pustakam.android.screen.bookUIView.AnnotatedBookPage
import com.app.pustakam.android.screen.bookUIView.BookScrollReader
import com.app.pustakam.android.screen.notebookReader.BookPager

import com.app.pustakam.android.screen.notebookReader.ReadingMode
import com.app.pustakam.android.theme.PaperColor
import com.app.pustakam.android.theme.typography
import com.app.pustakam.android.widgets.LoadingUI
import com.app.pustakam.android.widgets.SnackBarUi
import com.app.pustakam.android.widgets.drawing.DrawingChrome
import com.app.pustakam.android.widgets.drawing.DrawingPageLayer
import com.app.pustakam.android.widgets.drawing.rememberCapturesTouches
import com.app.pustakam.core.drawing.note.DrawNoteContents

@Composable
fun BookReaderScreen(
    modifier: Modifier = Modifier,
    bookId: String? = null,
    viewModel: BookReaderViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.bookUiState.collectAsStateWithLifecycle()
    val drawingTarget by viewModel.drawing.target.collectAsStateWithLifecycle()
    val annotation = viewModel.drawing.overlay.collectAsStateWithLifecycle().value
    val annotating = drawingTarget != null && drawingTarget == viewModel.drawing.overlayId()
    val inkCaptures = rememberCapturesTouches(annotation)
    val zoomEnabled = !(annotating && inkCaptures)
    BackHandler(enabled = annotating) { viewModel.drawing.stop() }

    LaunchedEffect(bookId) { viewModel.onHandleIntent(BookReaderIntent.LoadBook(bookId)) }

    Box(modifier.fillMaxSize().background(Color(0xFF241C14))) {   // dark desk behind the book
        when {
            state.pages.isEmpty() && state.isLoading -> LoadingUI()

            state.error != null -> SnackBarUi(error = state.error!!) {
                viewModel.clearError(); onBack()
            }

            state.pages.isNotEmpty() -> {
                var pageIndex by remember { mutableIntStateOf(state.pageProgress) }
                val doc = state.doc
                val pageInk: @Composable (Int) -> Unit = { index ->
                    if (annotation != null && doc != null) {
                        DrawingPageLayer(
                            session = annotation,
                            anchorId = DrawNoteContents.pageAnchorId(doc.id, index),
                            active = annotating,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                when (state.readingMode) {
                    ReadingMode.PAGE ->
                        BookPager(
                            pageCount = state.pages.size,
                            initialPage = pageIndex,
                            onPageChanged = { index ->
                                pageIndex = index
                                doc?.let { viewModel.onHandleIntent(BookReaderIntent.PageChanged(it, index)) }
                            },
                            zoomEnabled = zoomEnabled,
                        ) { index -> AnnotatedBookPage(page = state.pages[index]) { pageInk(index) } }

                    ReadingMode.SCROLL ->
                        BookScrollReader(
                            pages = state.pages,
                            startPageIndex = pageIndex,
                            onPageChanged = { index ->
                                pageIndex = index
                                doc?.let { viewModel.onHandleIntent(BookReaderIntent.PageChanged(it, index)) }
                            },
                            pageOverlay = pageInk,
                            zoomEnabled = zoomEnabled,
                        )
                }
                // page counter chip
                if (!annotating) Text(
                    "${pageIndex + 1} / ${state.pages.size}",
                    style = typography.labelMedium, color = PaperColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .background(Color.Black.copy(alpha = .45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close document", tint = colorScheme.secondary)
        }
        if (state.pages.isNotEmpty()) {
            IconButton(
                onClick = { viewModel.drawing.toggleOverlay() },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 54.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Write on document",
                    tint = if (annotating) colorScheme.primary else colorScheme.secondary
                )
            }
            IconButton(
                onClick = {
                    viewModel.onHandleIntent(BookReaderIntent.ToggleReadingMode(state.readingMode.toggled()))
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Icon(
                    if (state.readingMode == ReadingMode.PAGE)
                        Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = if (state.readingMode == ReadingMode.PAGE)
                        "Switch to scrolling" else "Switch to page curl",
                    tint = colorScheme.secondary
                )
            }
        }
        val inkSession = viewModel.drawing.active()
        if (annotating && inkSession != null) {
            DrawingChrome(
                session = inkSession,
                onDone = viewModel.drawing::stop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }
}
