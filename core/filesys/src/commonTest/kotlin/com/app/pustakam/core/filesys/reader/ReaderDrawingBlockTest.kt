package com.app.pustakam.core.filesys.reader

import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderDrawingBlockTest {

    private val policy = PageLayoutPolicy.standard()

    private fun text(id: String, position: Double) = NoteContentModel.TextContent(
        text = "a line", updatedAt = null, createdAt = null, id = id, noteId = "n", position = position,
    )

    private fun drawing(id: String, surface: String, width: Int, height: Int, position: Double) =
        NoteContentModel.Drawing(
            updatedAt = null, createdAt = null, id = id, noteId = "n", position = position,
            surface = surface, width = width, height = height,
        )

    @Test
    fun drawings_keep_reading_order_and_overlays_stay_out() {
        val contents = listOf(
            text("t1", 0.0),
            drawing("d1", NoteContentModel.Drawing.SURFACE_PAGE, 400, 800, 1.0),
            drawing("o1", NoteContentModel.Drawing.SURFACE_OVERLAY, 0, 0, 2.0),
            drawing("w1", NoteContentModel.Drawing.SURFACE_WIDGET, 300, 150, 3.0),
        )
        val blocks = ReaderBlockBuilder.buildBlocks(contents, policy)
        assertEquals(listOf("t1", "d1", "w1"), blocks.flatMap { it.sourceContentIds })
        assertIs<ReaderBlock.Drawing>(blocks[1])
        assertIs<ReaderBlock.Drawing>(blocks[2])
    }

    @Test
    fun a_drawing_takes_its_own_shape_within_the_page() {
        val tall = ReaderBlock.Drawing(drawing("p", NoteContentModel.Drawing.SURFACE_PAGE, 100, 1000, 0.0), listOf("p"))
        assertEquals(policy.usableHeight, BlockHeightEstimator.estimate(tall, policy))
        val wide = ReaderBlock.Drawing(drawing("w", NoteContentModel.Drawing.SURFACE_WIDGET, 1000, 10, 0.0), listOf("w"))
        assertEquals(policy.imageMinHeight, BlockHeightEstimator.estimate(wide, policy))
        val unsized = ReaderBlock.Drawing(drawing("u", NoteContentModel.Drawing.SURFACE_WIDGET, 0, 0, 0.0), listOf("u"))
        val expected = (policy.usableWidth * NoteContentModel.Drawing.DEFAULT_ASPECT)
            .coerceIn(policy.imageMinHeight, policy.usableHeight)
        assertEquals(expected, BlockHeightEstimator.estimate(unsized, policy))
        assertTrue(BlockHeightEstimator.size(unsized, policy).width == policy.usableWidth)
    }
}
