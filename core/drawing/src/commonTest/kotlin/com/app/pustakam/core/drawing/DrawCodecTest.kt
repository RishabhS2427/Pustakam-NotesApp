package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.codec.DrawCodec
import com.app.pustakam.core.drawing.codec.DrawPointCodec
import com.app.pustakam.core.drawing.color.DrawColorState
import com.app.pustakam.core.drawing.color.DrawPaletteCatalog
import com.app.pustakam.core.drawing.editor.DrawCommands
import com.app.pustakam.core.drawing.editor.DrawDocuments
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawPoint
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.tool.DrawEraserKind
import com.app.pustakam.core.drawing.tool.DrawToolSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawCodecTest {

    @Test
    fun pointsSurviveWithinQuantization() {
        val points = listOf(
            DrawPoint(10.123f, -5.5f, 0.42f, 0.3f, 1.2f, 1000L),
            DrawPoint(12.987f, -4.25f, 0.9f, 0.31f, 1.25f, 1016L),
            DrawPoint(-3000.5f, 88888.1f, 0f, 1.5f, 6.2f, 1040L)
        )
        val decoded = DrawPointCodec.decode(DrawPointCodec.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (original, copy) ->
            assertEquals(original.x, copy.x, 0.051f)
            assertEquals(original.y, copy.y, 0.051f)
            assertEquals(original.pressure, copy.pressure, 0.0051f)
            assertEquals(original.tilt, copy.tilt, 0.0051f)
            assertEquals(original.azimuth, copy.azimuth, 0.0051f)
        }
        assertEquals(listOf(0L, 16L, 40L), decoded.map { it.time })
    }

    @Test
    fun constantChannelsCostNothingAndDecodeToDefaults() {
        val flat = (0 until 200).map { DrawPoint.at(it * 1.5f, 40f) }
        val rich = flat.mapIndexed { index, point -> point.copy(pressure = 0.3f + index % 5 * 0.1f, time = index * 8L) }
        val flatText = DrawPointCodec.encode(flat)
        assertTrue(flatText.length < DrawPointCodec.encode(rich).length)
        val decoded = DrawPointCodec.decode(flatText)
        assertEquals(200, decoded.size)
        assertTrue(decoded.all { it.pressure == DrawPoint.DEFAULT_PRESSURE && it.tilt == 0f && it.azimuth == 0f && it.time == 0L })
        assertEquals(298.5f, decoded.last().x, 0.051f)
    }

    @Test
    fun garbagePointsDecodeToNothing() {
        assertTrue(DrawPointCodec.decode("").isEmpty())
        assertTrue(DrawPointCodec.decode("////").isEmpty())
    }

    @Test
    fun aDrawnDocumentRoundTrips() {
        var state = DrawTestKit.page()
        state = DrawTestKit.horizontal(state, 100f)
        state = DrawTestKit.reduce(state, DrawCommands.setShapeKind(DrawShapeKind.STAR))
        state = DrawTestKit.stroke(state, listOf(DrawTestKit.point(60f, 300f), DrawTestKit.point(160f, 420f)))
        state = DrawTestKit.reduce(state, DrawCommands.setPattern(state, DrawPattern.DOTTED))
        val document = state.document
        val decoded = DrawCodec.decode(DrawCodec.encode(document), DrawDocuments.overlay())
        assertEquals(document.id, decoded.id)
        assertEquals(DrawSurface.PAGE, decoded.surface)
        assertEquals(document.paper, decoded.paper)
        assertEquals(document.layers, decoded.layers)
        assertEquals(document.clock, decoded.clock)
        assertEquals(document.elements.map { it.id }, decoded.elements.map { it.id })
        document.elements.zip(decoded.elements).forEach { (original, copy) ->
            assertEquals(original.kind, copy.kind)
            assertEquals(original.shape, copy.shape)
            assertEquals(original.color, copy.color)
            assertEquals(original.brush.kind, copy.brush.kind)
            assertEquals(original.brush.size, copy.brush.size, 0.0001f)
            assertEquals(original.points.size, copy.points.size)
            assertEquals(original.seed, copy.seed)
        }
    }

    @Test
    fun corruptPayloadFallsBack() {
        val fallback = DrawDocuments.page(100f, 100f)
        assertEquals(fallback, DrawCodec.decode("{not json", fallback))
        assertEquals(fallback, DrawCodec.decode(null, fallback))
        assertEquals(fallback, DrawCodec.decode("[1,2,3]", fallback))
    }

    @Test
    fun unknownFieldsAndElementsAreTolerated() {
        val payload = """{"v":99,"id":"d1","surface":"WIDGET","future":true,"elements":[{"id":"e1","k":"HOLOGRAM"},{"nope":1}]}"""
        val decoded = DrawCodec.decode(payload, DrawDocuments.overlay())
        assertEquals("d1", decoded.id)
        assertEquals(DrawSurface.WIDGET, decoded.surface)
        assertEquals(listOf("e1"), decoded.elements.map { it.id })
        assertTrue(decoded.layers.isNotEmpty())
    }

    @Test
    fun opsRoundTripForTheWire() {
        val state = DrawTestKit.horizontal(DrawTestKit.page(), 80f)
        val added = state.lastOp as DrawOp.Add
        val decoded = assertNotNull(DrawCodec.decodeOp(DrawCodec.encodeOp(added))) as DrawOp.Add
        assertEquals(added.author, decoded.author)
        assertEquals(added.clock, decoded.clock)
        assertEquals(added.elements.map { it.id }, decoded.elements.map { it.id })
        val paper = DrawOp.Paper(state.document.paper, state.document.paper.withPattern(DrawPattern.GRID), "b", 9L)
        assertEquals(paper, DrawCodec.decodeOp(DrawCodec.encodeOp(paper)))
        assertNull(DrawCodec.decodeOp("{\"t\":\"TELEPORT\"}"))
    }

    @Test
    fun toolboxRoundTripsCustomisationsOnly() {
        val red = DrawColor.fromRgb(200, 10, 10)
        val settings = DrawToolSettings.defaults()
            .withBrushKind(DrawBrushKind.CHALK)
            .withBrush(DrawToolSettings.defaults().brushOf(DrawBrushKind.CHALK).withSize(33f))
            .withEraserKind(DrawEraserKind.PARTIAL)
        val colors = DrawColorState.defaults().withCurrent(red).used(red).toggledFavorite(red)
            .withPalette(DrawPaletteCatalog.custom("p1", "Mine", listOf(red)))
        val payload = DrawCodec.encodeToolbox(settings, colors)
        val restoredSettings = DrawCodec.decodeToolSettings(payload, DrawToolSettings.defaults())
        val restoredColors = DrawCodec.decodeColors(payload, DrawColorState.defaults())
        assertEquals(DrawBrushKind.CHALK, restoredSettings.brushKind)
        assertEquals(33f, restoredSettings.brushOf(DrawBrushKind.CHALK).size, 0.0001f)
        assertEquals(DrawEraserKind.PARTIAL, restoredSettings.eraserKind)
        assertEquals(red, restoredColors.current)
        assertEquals(listOf(red), restoredColors.recent)
        assertEquals(listOf(red), restoredColors.favorites)
        assertEquals(DrawColorState.defaults().palettes.size + 1, restoredColors.palettes.size)
    }
}
