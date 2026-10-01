package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawToolbarLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawToolbarPlacementTest {

    private val margin = DrawToolbarLayout.MARGIN

    @Test
    fun aHorizontalToolbarStartsCenteredAboveTheBottomEdge() {
        val placed = DrawCommands.fitToolbar(DrawCommands.toolbarPlacement(), 300f, 48f, 400f, 800f)
        assertTrue(placed.placed)
        assertEquals(50f, placed.left(300f), 0.01f)
        assertEquals(800f - margin - 48f, placed.top(48f), 0.01f)
    }

    @Test
    fun aVerticalToolbarStartsAgainstTheRightEdge() {
        val vertical = DrawCommands.rotateToolbar(DrawCommands.toolbarPlacement())
        val placed = DrawCommands.fitToolbar(vertical, 48f, 300f, 400f, 800f)
        assertTrue(placed.vertical)
        assertEquals(400f - margin - 48f, placed.left(48f), 0.01f)
        assertEquals(250f, placed.top(300f), 0.01f)
    }

    @Test
    fun draggingNeverLeavesTheVisibleArea() {
        val placed = DrawCommands.fitToolbar(DrawCommands.toolbarPlacement(), 300f, 48f, 400f, 800f)
        val pushed = DrawCommands.moveToolbar(placed, -1000f, -5000f, 300f, 48f, 400f, 800f)
        assertEquals(margin, pushed.left(300f), 0.01f)
        assertEquals(margin, pushed.top(48f), 0.01f)
        val back = DrawCommands.moveToolbar(pushed, 5000f, 5000f, 300f, 48f, 400f, 800f)
        assertEquals(400f - margin - 300f, back.left(300f), 0.01f)
        assertEquals(800f - margin - 48f, back.top(48f), 0.01f)
    }

    @Test
    fun turningKeepsTheCenterAndRefitsInsideTheArea() {
        val placed = DrawCommands.fitToolbar(DrawCommands.toolbarPlacement(), 300f, 48f, 400f, 800f)
        val turned = DrawCommands.rotateToolbar(placed)
        assertTrue(turned.vertical)
        assertEquals(placed.centerX, turned.centerX, 0.01f)
        val refit = DrawCommands.fitToolbar(turned, 48f, 300f, 400f, 800f)
        assertTrue(refit.top(300f) >= margin)
        assertTrue(refit.top(300f) + 300f <= 800f - margin)
        assertFalse(DrawCommands.rotateToolbar(refit).vertical)
    }

    @Test
    fun anAreaTooSmallCentersTheToolbarAndUnmeasuredLeavesItAlone() {
        val placed = DrawCommands.fitToolbar(DrawCommands.toolbarPlacement(), 500f, 48f, 400f, 800f)
        assertEquals(200f, placed.centerX, 0.01f)
        val untouched = DrawCommands.fitToolbar(DrawCommands.toolbarPlacement(), 0f, 0f, 400f, 800f)
        assertFalse(untouched.placed)
        assertEquals(384f, DrawCommands.toolbarMaxLength(400f), 0.01f)
    }
}
