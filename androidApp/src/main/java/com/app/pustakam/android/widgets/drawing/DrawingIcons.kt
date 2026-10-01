package com.app.pustakam.android.widgets.drawing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object DrawingIcons {

    private const val PEN_KEY = "tool_pen"

    private const val ERASER_KEY = "tool_eraser"

    private const val SHAPE_KEY = "tool_shape"

    private const val HAND_KEY = "tool_hand"

    val Eraser: ImageVector by lazy {
        ImageVector.Builder(
            name = "DrawingEraser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 3.5f)
            lineTo(20.5f, 10f)
            lineTo(11.5f, 19f)
            lineTo(5f, 12.5f)
            close()
            moveTo(8.2f, 9.3f)
            lineTo(14.7f, 15.8f)
            moveTo(11.5f, 20.5f)
            lineTo(20f, 20.5f)
        }.build()
    }

    fun tool(iconKey: String): ImageVector = when (iconKey) {
        PEN_KEY -> Icons.Default.Edit
        ERASER_KEY -> Eraser
        SHAPE_KEY -> Icons.Default.Category
        HAND_KEY -> Icons.Default.PanTool
        else -> Icons.Default.Edit
    }
}
