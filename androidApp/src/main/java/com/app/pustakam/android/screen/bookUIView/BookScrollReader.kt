package com.app.pustakam.android.screen.bookUIView

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp
import com.app.pustakam.android.screen.notebookReader.BookPage

import com.app.pustakam.android.widgets.zoom.zoomable

@Composable
fun BookScrollReader(
    pages: List<BookPage>,
    startPageIndex: Int,
    onPageChanged: (Int) -> Unit,
    pageOverlay: @Composable (Int) -> Unit = {},
    zoomEnabled: Boolean = true,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { onPageChanged(it) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().zoomable(zoomEnabled),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(pages.size) { index ->
            Box(Modifier.fillMaxWidth().height(560.dp)) {
                AnnotatedBookPage(page = pages[index]) { pageOverlay(index) }
            }
        }
    }
}
