package com.app.pustakam.android.screen.notebookReader


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.app.pustakam.android.widgets.zoom.zoomable
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private enum class FlipDirection { FORWARD, BACKWARD }

@Composable
fun BookPager(
    modifier: Modifier = Modifier,
    pageCount: Int,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    // 📖 15-Aug-2026: only inline documents resize the list under the pager; off, nothing changes
    inlineDocumentsEnabled: Boolean = false,
    zoomEnabled: Boolean = true,
    pageContent: @Composable (Int) -> Unit,
) {
    if (pageCount <= 0) return
    var currentPage by remember { mutableIntStateOf(initialPage.coerceIn(0, pageCount - 1).coerceAtLeast(0)) }

    if (inlineDocumentsEnabled) {
        // the caller moves [initialPage] deliberately (expand / collapse / load more) — follow it,
        // and never keep an index the resized list no longer holds
        LaunchedEffect(pageCount, initialPage) {
            val target = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (target != currentPage || currentPage > pageCount - 1) currentPage = target
        }
    }

    val flip = remember { Animatable(0f) }
    var direction by remember { mutableStateOf<FlipDirection?>(null) }
    var containerWidth by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    fun settle(complete: Boolean) {
        val dir = direction ?: return
        scope.launch {
            if (complete) {
                flip.animateTo(if (dir == FlipDirection.FORWARD) 1f else 0f, tween(360))
                val newPage = when (dir) {
                    FlipDirection.FORWARD -> (currentPage + 1).coerceAtMost(pageCount - 1)
                    FlipDirection.BACKWARD -> (currentPage - 1).coerceAtLeast(0)
                }
                direction = null
                currentPage = newPage
                flip.snapTo(0f)
                onPageChanged(newPage)
            } else {
                flip.animateTo(if (dir == FlipDirection.FORWARD) 0f else 1f, tween(360))
                direction = null
                flip.snapTo(0f)
            }
        }
    }

    fun flipBy(dir: FlipDirection) {
        if (direction != null) return
        if (dir == FlipDirection.FORWARD && currentPage >= pageCount - 1) return
        if (dir == FlipDirection.BACKWARD && currentPage <= 0) return
        direction = dir
        scope.launch {
            flip.snapTo(if (dir == FlipDirection.FORWARD) 0f else 1f)
            settle(complete = true)
        }
    }

    Box(
        modifier = modifier
            .zoomable(zoomEnabled)
            .fillMaxSize()
            .pointerInput(pageCount) {
                containerWidth = size.width.toFloat()
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        when (direction) {
                            FlipDirection.FORWARD -> settle(complete = flip.value > 0.25f)
                            FlipDirection.BACKWARD -> settle(complete = flip.value < 0.75f)
                            null -> {}
                        }
                    },
                    onDragCancel = { settle(complete = false) },
                ) { change, delta ->
                    change.consume()
                    dragTotal += delta
                    if (direction == null) {
                        // 🔧 18-Jul-2026: leftward drag turns forward; rightward brings the leaf back
                        direction = when {
                            dragTotal < -12f && currentPage < pageCount - 1 -> FlipDirection.FORWARD
                            dragTotal > 12f && currentPage > 0 -> FlipDirection.BACKWARD
                            else -> null
                        }
                        if (direction == FlipDirection.BACKWARD) scope.launch { flip.snapTo(1f) }
                    }
                    direction?.let { dir ->
                        val progress = when (dir) {
                            FlipDirection.FORWARD -> (-dragTotal / containerWidth).coerceIn(0f, 1f)
                            FlipDirection.BACKWARD -> (1f - dragTotal / containerWidth).coerceIn(0f, 1f)
                        }
                        scope.launch { flip.snapTo(progress) }
                    }
                }
            }
            .pointerInput(pageCount) {
                // 🔧 18-Jul-2026: edge taps = flip (right → next page, left → previous page)
                detectTapGestures { offset ->
                    when {
                        offset.x > size.width * 0.78f -> flipBy(FlipDirection.FORWARD)
                        offset.x < size.width * 0.22f -> flipBy(FlipDirection.BACKWARD)
                    }
                }
            }
    ) {
        val dir = direction
        val progress = flip.value
        // 📖 15-Aug-2026: read through a clamped index — a resized list can never be indexed stale
        val page = currentPage.coerceIn(0, pageCount - 1)
        val leafFrontPage = if (dir == FlipDirection.BACKWARD) page - 1 else page
        val leafBackPage = if (dir == FlipDirection.BACKWARD) page else page + 1
        val basePage = if (dir == FlipDirection.BACKWARD) page else (page + 1).coerceAtMost(pageCount - 1)

        if (dir == null) {
            Box(Modifier.fillMaxSize()) { pageContent(page) }
        } else {
            // base layer — what lies under the turning leaf
            Box(Modifier.fillMaxSize()) { pageContent(basePage) }
            // spine shadow on the base grows as the leaf lifts
            Box(
                Modifier.fillMaxWidth()
                    .fillMaxHeight()
                    .width(32.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer { alpha = sin(PI * progress).toFloat() * 0.5f }
                    .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent)))
            )
            val angle = -180f * progress
            if (progress < 0.5f) {
                // front face of the leaf (readable side before the fold passes 90°)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = angle
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = 24f * density.density
                        }
                ) {
                    pageContent(leafFrontPage)
                    // 🔧 18-Jul-2026: paper shading — the leaf darkens as it lifts toward 90°
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (progress * 2f).coerceIn(0f, 1f) * 0.35f }
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black))))
                }
            } else {
                // back face of the leaf — shows the destination page, mirrored into place
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = angle + 180f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = 24f * density.density
                        }
                ) {
                    pageContent(leafBackPage)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = ((1f - progress) * 2f).coerceIn(0f, 1f) * 0.35f }
                            .background(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent))))
                }
            }
        }
    }
}
