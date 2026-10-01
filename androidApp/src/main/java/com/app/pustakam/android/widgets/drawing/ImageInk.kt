package com.app.pustakam.android.widgets.drawing

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.app.pustakam.core.drawing.render.DrawRenderEntry

fun renderInk(base: Bitmap, entries: List<DrawRenderEntry>): Bitmap {
    val width = base.width.coerceAtLeast(1)
    val height = base.height.coerceAtLeast(1)
    val target = ImageBitmap(width, height)
    val canvas = Canvas(target)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
        drawImage(base.asImageBitmap())
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawEntries(entries, null)
        canvas.restore()
    }
    return target.asAndroidBitmap()
}
