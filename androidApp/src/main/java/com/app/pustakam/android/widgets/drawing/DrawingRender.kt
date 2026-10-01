package com.app.pustakam.android.widgets.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.geometry.DrawMatrix
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.render.DrawCap
import com.app.pustakam.core.drawing.render.DrawJoin
import com.app.pustakam.core.drawing.render.DrawRenderEntry
import com.app.pustakam.core.drawing.render.DrawRenderItem
import java.util.IdentityHashMap
import kotlin.math.roundToInt

private const val SOFT_EDGE = 0.95f

class DrawingPathCache {
    private var current = IdentityHashMap<DrawRenderItem, Path>()
    private var next = IdentityHashMap<DrawRenderItem, Path>()

    fun pathOf(item: DrawRenderItem): Path {
        val path = current[item] ?: next[item] ?: drawingPathOf(item.path)
        next[item] = path
        return path
    }

    fun endFrame() {
        current = next
        next = IdentityHashMap()
    }
}

class DrawingCommittedLayer {
    private var key: String? = null
    private var bitmap: ImageBitmap? = null

    fun bitmapFor(scope: DrawScope, newKey: String, content: DrawScope.() -> Unit): ImageBitmap {
        val width = scope.size.width.toInt().coerceAtLeast(1)
        val height = scope.size.height.toInt().coerceAtLeast(1)
        val existing = bitmap
        val sameSize = existing != null && existing.width == width && existing.height == height
        if (existing != null && sameSize && key == newKey) return existing
        val target = if (existing != null && sameSize) existing else ImageBitmap(width, height)
        CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(target), Size(width.toFloat(), height.toFloat())) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            content()
        }
        bitmap = target
        key = newKey
        return target
    }
}

fun drawingPathOf(data: FloatArray): Path {
    val path = Path()
    var index = 0
    while (index < data.size) {
        val command = data[index]
        when {
            DrawCommands.isMove(command) -> path.moveTo(data[index + 1], data[index + 2])
            DrawCommands.isLine(command) -> path.lineTo(data[index + 1], data[index + 2])
            DrawCommands.isQuad(command) ->
                path.quadraticBezierTo(data[index + 1], data[index + 2], data[index + 3], data[index + 4])
            DrawCommands.isCubic(command) ->
                path.cubicTo(data[index + 1], data[index + 2], data[index + 3], data[index + 4], data[index + 5], data[index + 6])
            DrawCommands.isClose(command) -> path.close()
        }
        index += 1 + DrawCommands.pathStride(command)
    }
    return path
}

fun DrawScope.drawEntries(entries: List<DrawRenderEntry>, paths: DrawingPathCache?) {
    val currentDensity = density
    for (entry in entries) {
        val item = entry.item
        withTransform({ transform(entry.matrix.toCompose(currentDensity)) }) {
            if (DrawCommands.isDabs(item)) {
                drawDabs(item)
            } else {
                val path = paths?.pathOf(item) ?: drawingPathOf(item.path)
                val style = if (DrawCommands.isStroke(item)) {
                    Stroke(width = item.strokeWidth, cap = item.cap.toCompose(), join = item.join.toCompose())
                } else {
                    Fill
                }
                drawPath(path, color = item.color.toCompose(item.opacity), style = style, blendMode = item.blend.toCompose())
            }
        }
    }
    paths?.endFrame()
}

private fun DrawScope.drawDabs(item: DrawRenderItem) {
    val dabs = item.dabs
    if (dabs.isEmpty()) return
    val base = item.color.toCompose(1f)
    val bounds = item.bounds
    val canvas = drawContext.canvas
    canvas.saveLayer(
        Rect(bounds.x, bounds.y, bounds.right, bounds.bottom),
        Paint().apply {
            alpha = item.opacity.coerceIn(0f, 1f)
            blendMode = item.blend.toCompose()
        }
    )
    val stride = DrawCommands.dabStride()
    val soft = item.hardness < SOFT_EDGE
    var index = 0
    while (index + stride <= dabs.size) {
        val center = Offset(dabs[index], dabs[index + 1])
        val radius = dabs[index + 2]
        val color = base.copy(alpha = base.alpha * dabs[index + 3])
        if (soft) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color,
                    item.hardness.coerceIn(0f, SOFT_EDGE) to color,
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        } else {
            drawCircle(color = color, radius = radius, center = center)
        }
        index += stride
    }
    canvas.restore()
}

fun DrawMatrix.toCompose(density: Float): Matrix {
    val matrix = Matrix()
    matrix.values[Matrix.ScaleX] = a * density
    matrix.values[Matrix.SkewY] = b * density
    matrix.values[Matrix.SkewX] = c * density
    matrix.values[Matrix.ScaleY] = d * density
    matrix.values[Matrix.TranslateX] = tx * density
    matrix.values[Matrix.TranslateY] = ty * density
    return matrix
}

fun DrawColor.toCompose(opacity: Float): Color {
    val color = Color(argbInt)
    return color.copy(alpha = (color.alpha * opacity).coerceIn(0f, 1f))
}

fun Color.toDrawColor(): DrawColor = DrawCommands.colorFromRgb(
    (red * 255f).roundToInt(),
    (green * 255f).roundToInt(),
    (blue * 255f).roundToInt(),
    alpha
)

fun DrawBlend.toCompose(): BlendMode = when (this) {
    DrawBlend.NORMAL -> BlendMode.SrcOver
    DrawBlend.MULTIPLY -> BlendMode.Multiply
    DrawBlend.SCREEN -> BlendMode.Screen
    DrawBlend.OVERLAY -> BlendMode.Overlay
    DrawBlend.SOFT_LIGHT -> BlendMode.Softlight
    DrawBlend.HARD_LIGHT -> BlendMode.Hardlight
    DrawBlend.DARKEN -> BlendMode.Darken
    DrawBlend.LIGHTEN -> BlendMode.Lighten
    DrawBlend.COLOR -> BlendMode.Color
    DrawBlend.DIFFERENCE -> BlendMode.Difference
    DrawBlend.CLEAR -> BlendMode.Clear
}

fun DrawCap.toCompose(): StrokeCap = when (this) {
    DrawCap.BUTT -> StrokeCap.Butt
    DrawCap.ROUND -> StrokeCap.Round
    DrawCap.SQUARE -> StrokeCap.Square
}

fun DrawJoin.toCompose(): StrokeJoin = when (this) {
    DrawJoin.MITER -> StrokeJoin.Miter
    DrawJoin.ROUND -> StrokeJoin.Round
    DrawJoin.BEVEL -> StrokeJoin.Bevel
}
