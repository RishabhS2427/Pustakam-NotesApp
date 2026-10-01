package com.app.pustakam.android.screen.bookUIView

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.app.pustakam.android.screen.notebookReader.BookPage

@Composable
fun AnnotatedBookPage(page: BookPage, overlay: @Composable () -> Unit) {
    when (page) {
        is BookPage.PdfSheet -> PaperPage(background = Color.White) { PdfBookPage(page, overlay) }
        else -> Box(Modifier.fillMaxSize()) {
            BookPageContent(page = page)
            overlay()
        }
    }
}
