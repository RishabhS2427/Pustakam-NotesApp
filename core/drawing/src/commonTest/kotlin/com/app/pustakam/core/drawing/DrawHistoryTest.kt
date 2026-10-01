package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.editor.DrawDocuments
import com.app.pustakam.core.drawing.history.DrawHistory
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawLayer
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.ops.DrawOpApplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawHistoryTest {

    private fun element(id: String, clock: Long, author: String = "a", color: DrawColor = DrawColor.BLACK) = DrawElement(
        id = id,
        kind = DrawElementKind.STROKE,
        layerId = DrawLayer.BASE_ID,
        anchorId = null,
        points = listOf(DrawPoint.at(0f, 0f), DrawPoint.at(10f, 10f)),
        brush = DrawBrushCatalog.defaultBrush(DrawBrushKind.BALLPOINT),
        color = color,
        shape = DrawShapeSpec.none(),
        order = clock.toDouble(),
        author = author,
        clock = clock,
        seed = 1
    )

    @Test
    fun undoAndRedoWalkTheStack() {
        val first = DrawOp.Add(listOf(element("1", 1)), "a", 1)
        val second = DrawOp.Add(listOf(element("2", 2)), "a", 2)
        val history = DrawHistory.empty().record(first).record(second)
        val undone = history.undoStep()!!
        assertEquals(second, undone.op)
        assertTrue(undone.history.canRedo)
        val redone = undone.history.redoStep()!!
        assertEquals(second, redone.op)
        assertFalse(redone.history.canRedo)
        assertNull(DrawHistory.empty().undoStep())
    }

    @Test
    fun recordingClearsTheFutureAndRespectsTheLimit() {
        var history = DrawHistory.empty(3)
        repeat(5) { history = history.record(DrawOp.Add(listOf(element("$it", it.toLong())), "a", it.toLong())) }
        assertEquals(3, history.past.size)
        val branched = history.undoStep()!!.history.record(DrawOp.Add(emptyList(), "a", 9))
        assertFalse(branched.canRedo)
    }

    @Test
    fun inversesUndoEveryOpKind() {
        val base = DrawOpApplier.apply(DrawDocuments.page(100f, 100f), DrawOp.Add(listOf(element("1", 1)), "a", 1))
        val ops = listOf(
            DrawOp.Add(listOf(element("2", 2)), "a", 2),
            DrawOp.Remove(listOf(element("1", 1)), "a", 2),
            DrawOp.Replace(listOf(element("1", 1)), listOf(element("3", 2), element("4", 2)), "a", 2),
            DrawOp.Paper(base.paper, base.paper.withSpacing(40f), "a", 2)
        )
        ops.forEach { op ->
            val applied = DrawOpApplier.apply(base, op)
            val restored = DrawOpApplier.apply(applied, op.inverse("a", 3))
            assertEquals(base.elements.map { it.id }.toSet(), restored.elements.map { it.id }.toSet(), "${op.kind}")
            assertEquals(base.paper, restored.paper)
        }
    }

    @Test
    fun applyingIsIdempotentAndLastWriterWins() {
        val add = DrawOp.Add(listOf(element("1", 5)), "a", 5)
        val once = DrawOpApplier.apply(DrawDocuments.page(10f, 10f), add)
        val twice = DrawOpApplier.apply(once, add)
        assertEquals(once.elements, twice.elements)
        val stale = DrawOpApplier.apply(twice, DrawOp.Add(listOf(element("1", 3, color = DrawColor.WHITE)), "b", 3))
        assertEquals(DrawColor.BLACK, stale.elementById("1")!!.color)
        val fresh = DrawOpApplier.apply(twice, DrawOp.Add(listOf(element("1", 8, color = DrawColor.WHITE)), "b", 8))
        assertEquals(DrawColor.WHITE, fresh.elementById("1")!!.color)
        assertEquals(8L, fresh.clock)
        val removedTwice = DrawOpApplier.apply(DrawOpApplier.apply(once, add.inverse("a", 6)), add.inverse("a", 7))
        assertTrue(removedTwice.isEmpty)
    }
}
