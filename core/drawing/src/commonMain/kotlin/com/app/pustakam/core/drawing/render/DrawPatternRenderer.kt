package com.app.pustakam.core.drawing.render

import com.app.pustakam.core.drawing.geometry.DrawPathBuilder
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawTexture
import com.app.pustakam.core.richtext.master.model.CanvasRect
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

object DrawPatternRenderer {

    private const val BACKGROUND_KEY = "paper:background"

    private const val PATTERN_KEY = "paper:pattern"

    private const val MARGIN_KEY = "paper:margin"

    private const val TEXTURE_KEY = "paper:texture"

    private const val TEXTURE_CELL = 5f

    private const val MAX_TEXTURE_CELLS = 90f

    private const val PAPER_GRAIN_PERCENT = 30

    private const val KRAFT_GRAIN_PERCENT = 55

    private const val HASH_SPAN = 1000

    private val PAPER_GRAIN = DrawColor(0x18000000L)

    private val KRAFT_FIBRE = DrawColor(0x305C3A12L)

    private val CANVAS_WEAVE = DrawColor(0x14000000L)

    private const val HAIRLINE = 0.75f

    private const val MIN_VIEW_SPACING = 5f

    private const val MAX_LINES = 400

    private const val MARGIN_COLUMNS = 3f

    private const val PERSPECTIVE_STEP_DEGREES = 10

    private val ISOMETRIC_SLOPE = tan(PI / 6.0).toFloat()

    fun items(paper: DrawPaper, visible: CanvasRect, scale: Float): List<DrawRenderItem> {
        val safeScale = if (scale > 0f) scale else 1f
        val region = regionOf(paper, visible) ?: return emptyList()
        val items = mutableListOf<DrawRenderItem>()
        if (!paper.transparent) {
            val area = if (paper.isInfinite) region else paper.bounds
            items.add(
                DrawRenderItem.fill(BACKGROUND_KEY, rectanglePath(area), paper.background, 1f, DrawBlend.NORMAL, area)
            )
            texture(paper, region)?.let(items::add)
        }
        if (paper.pattern == DrawPattern.NONE || paper.spacing * safeScale < MIN_VIEW_SPACING) return items
        val width = HAIRLINE / safeScale
        when (paper.pattern) {
            DrawPattern.GRID -> items.add(lines(PATTERN_KEY, gridPath(region, paper.spacing, vertical = true), paper, width, region))
            DrawPattern.RULED -> {
                items.add(lines(PATTERN_KEY, gridPath(region, paper.spacing, vertical = false), paper, width, region))
                if (!paper.isInfinite) items.add(lines(MARGIN_KEY, marginPath(paper, region), paper, width * 1.5f, region))
            }
            DrawPattern.DOTTED -> items.add(dots(paper, region, width))
            DrawPattern.ISOMETRIC -> items.add(lines(PATTERN_KEY, isometricPath(region, paper.spacing), paper, width, region))
            DrawPattern.PERSPECTIVE -> items.add(lines(PATTERN_KEY, perspectivePath(paper, region), paper, width, region))
            DrawPattern.NONE -> Unit
        }
        return items
    }

    private fun regionOf(paper: DrawPaper, visible: CanvasRect): CanvasRect? {
        if (paper.isInfinite) return visible.takeIf { it.width > 0f && it.height > 0f }
        val bounds = paper.bounds
        if (visible.width <= 0f || visible.height <= 0f) return bounds
        val left = max(bounds.x, visible.x)
        val top = max(bounds.y, visible.y)
        val right = min(bounds.right, visible.right)
        val bottom = min(bounds.bottom, visible.bottom)
        if (right <= left || bottom <= top) return null
        return CanvasRect(left, top, right - left, bottom - top)
    }

    private fun texture(paper: DrawPaper, region: CanvasRect): DrawRenderItem? {
        val step = textureStep(max(region.width, region.height))
        return when (paper.texture) {
            DrawTexture.NONE -> null
            DrawTexture.PAPER -> grain(region, step, PAPER_GRAIN_PERCENT, PAPER_GRAIN)
            DrawTexture.KRAFT -> grain(region, step, KRAFT_GRAIN_PERCENT, KRAFT_FIBRE)
            DrawTexture.CANVAS -> DrawRenderItem.stroke(
                TEXTURE_KEY,
                gridPath(region, step, vertical = true),
                CANVAS_WEAVE,
                1f,
                step * 0.35f,
                DrawCap.BUTT,
                DrawJoin.MITER,
                DrawBlend.NORMAL,
                region
            )
        }
    }

    private fun textureStep(span: Float): Float {
        var step = TEXTURE_CELL
        while (span / step > MAX_TEXTURE_CELLS) step *= 2f
        return step
    }

    private fun grain(region: CanvasRect, step: Float, percent: Int, color: DrawColor): DrawRenderItem {
        val values = ArrayList<Float>()
        val firstColumn = floor(region.x / step).toInt()
        val lastColumn = ceil(region.right / step).toInt()
        val firstRow = floor(region.y / step).toInt()
        val lastRow = ceil(region.bottom / step).toInt()
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val hash = mixed(column, row)
                if (hash % 100 >= percent) continue
                values.add((column + fraction(hash, 1)) * step)
                values.add((row + fraction(hash, 2)) * step)
                values.add(step * (0.12f + 0.18f * fraction(hash, 3)))
                values.add(0.4f + 0.6f * fraction(hash, 4))
            }
        }
        return DrawRenderItem.dabs(TEXTURE_KEY, values.toFloatArray(), color, 1f, 1f, DrawBlend.NORMAL, region)
    }

    private fun mixed(column: Int, row: Int): Int {
        var hash = column * 73856093 xor row * 19349663
        hash = hash xor (hash ushr 16)
        hash *= -2048144789
        hash = hash xor (hash ushr 13)
        hash *= -1028477387
        hash = hash xor (hash ushr 16)
        return hash and Int.MAX_VALUE
    }

    private fun fraction(hash: Int, salt: Int): Float = ((hash / (salt * 7 + 3)) % HASH_SPAN) / HASH_SPAN.toFloat()

    private fun lines(key: String, path: FloatArray, paper: DrawPaper, width: Float, region: CanvasRect) =
        DrawRenderItem.stroke(key, path, paper.patternColor, 1f, width, DrawCap.BUTT, DrawJoin.MITER, DrawBlend.NORMAL, region)

    private fun stepFor(span: Float, spacing: Float): Float {
        val count = span / spacing
        return if (count <= MAX_LINES) spacing else spacing * ceil(count / MAX_LINES)
    }

    private fun gridPath(region: CanvasRect, spacing: Float, vertical: Boolean): FloatArray {
        val builder = DrawPathBuilder()
        val stepY = stepFor(region.height, spacing)
        var y = floor(region.y / stepY) * stepY
        while (y <= region.bottom) {
            if (y >= region.y) builder.moveTo(region.x, y).lineTo(region.right, y)
            y += stepY
        }
        if (vertical) {
            val stepX = stepFor(region.width, spacing)
            var x = floor(region.x / stepX) * stepX
            while (x <= region.right) {
                if (x >= region.x) builder.moveTo(x, region.y).lineTo(x, region.bottom)
                x += stepX
            }
        }
        return builder.build()
    }

    private fun marginPath(paper: DrawPaper, region: CanvasRect): FloatArray {
        val x = paper.spacing * MARGIN_COLUMNS
        if (x < region.x || x > region.right) return FloatArray(0)
        return DrawPathBuilder().moveTo(x, region.y).lineTo(x, region.bottom).build()
    }

    private fun dots(paper: DrawPaper, region: CanvasRect, width: Float): DrawRenderItem {
        val stepX = stepFor(region.width, paper.spacing)
        val stepY = stepFor(region.height, paper.spacing)
        val values = ArrayList<Float>()
        var y = ceil(region.y / stepY) * stepY
        while (y <= region.bottom) {
            var x = ceil(region.x / stepX) * stepX
            while (x <= region.right) {
                values.add(x)
                values.add(y)
                values.add(width * 1.4f)
                values.add(1f)
                x += stepX
            }
            y += stepY
        }
        return DrawRenderItem.dabs(PATTERN_KEY, values.toFloatArray(), paper.patternColor, 1f, 1f, DrawBlend.NORMAL, region)
    }

    private fun isometricPath(region: CanvasRect, spacing: Float): FloatArray {
        val builder = DrawPathBuilder()
        val stepX = stepFor(region.width, spacing)
        var x = floor(region.x / stepX) * stepX
        while (x <= region.right) {
            if (x >= region.x) builder.moveTo(x, region.y).lineTo(x, region.bottom)
            x += stepX
        }
        val intercept = stepFor(region.height + region.width * ISOMETRIC_SLOPE, spacing / cos(PI / 6.0).toFloat())
        for (slope in floatArrayOf(ISOMETRIC_SLOPE, -ISOMETRIC_SLOPE)) {
            val lowest = min(region.y - slope * region.x, region.y - slope * region.right)
            val highest = max(region.bottom - slope * region.x, region.bottom - slope * region.right)
            var c = floor(lowest / intercept) * intercept
            while (c <= highest) {
                clipped(region, region.x, slope * region.x + c, region.right, slope * region.right + c)?.let {
                    builder.moveTo(it[0], it[1]).lineTo(it[2], it[3])
                }
                c += intercept
            }
        }
        return builder.build()
    }

    private fun perspectivePath(paper: DrawPaper, region: CanvasRect): FloatArray {
        val builder = DrawPathBuilder()
        val center = if (paper.isInfinite) region else paper.bounds
        val vx = center.centerX
        val vy = center.centerY
        val reach = (region.width + region.height + center.width + center.height) * 2f
        clipped(region, region.x, vy, region.right, vy)?.let { builder.moveTo(it[0], it[1]).lineTo(it[2], it[3]) }
        var degrees = 0
        while (degrees < 360) {
            val angle = degrees * PI.toFloat() / 180f
            clipped(region, vx, vy, vx + reach * cos(angle), vy + reach * sin(angle))?.let {
                builder.moveTo(it[0], it[1]).lineTo(it[2], it[3])
            }
            degrees += PERSPECTIVE_STEP_DEGREES
        }
        return builder.build()
    }

    private fun rectanglePath(rect: CanvasRect): FloatArray = DrawPathBuilder()
        .moveTo(rect.x, rect.y)
        .lineTo(rect.right, rect.y)
        .lineTo(rect.right, rect.bottom)
        .lineTo(rect.x, rect.bottom)
        .close()
        .build()

    private fun clipped(rect: CanvasRect, x0: Float, y0: Float, x1: Float, y1: Float): FloatArray? {
        var t0 = 0f
        var t1 = 1f
        val dx = x1 - x0
        val dy = y1 - y0
        val checks = arrayOf(
            floatArrayOf(-dx, x0 - rect.x),
            floatArrayOf(dx, rect.right - x0),
            floatArrayOf(-dy, y0 - rect.y),
            floatArrayOf(dy, rect.bottom - y0)
        )
        for (check in checks) {
            val p = check[0]
            val q = check[1]
            if (p == 0f) {
                if (q < 0f) return null
            } else {
                val t = q / p
                if (p < 0f) {
                    if (t > t1) return null
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return null
                    if (t < t1) t1 = t
                }
            }
        }
        return floatArrayOf(x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy)
    }
}
