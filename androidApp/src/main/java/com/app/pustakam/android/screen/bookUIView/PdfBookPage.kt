package com.app.pustakam.android.screen.bookUIView

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.screen.notebookReader.BookPage
import com.app.pustakam.android.widgets.LoadingUI

@Composable
fun PdfBookPage(page: BookPage.PdfSheet, overlay: @Composable () -> Unit = {}) {
    var bitmap by remember(page.path, page.pageIndex) { mutableStateOf<Bitmap?>(null) }
    // 📖 15-Aug-2026: rendering moved to PdfPageRenderer — same output, now cached and gated
    LaunchedEffect(page.path, page.pageIndex) {
        bitmap = PdfPageRenderer.render(page.path, page.pageIndex, PdfPageRenderer.FULL_SCREEN_WIDTH_PX)
    }
    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        bitmap?.let {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(it.width.toFloat() / it.height.coerceAtLeast(1))) {
                    Image(
                        bitmap = it.asImageBitmap(), contentDescription = "${page.title} page ${page.pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    overlay()
                }
            }
        } ?: Box(Modifier.weight(1f).fillMaxWidth()) { LoadingUI() }
    }
}
