package com.app.pustakam.core.drawing

import com.app.pustakam.core.drawing.color.DrawColorState
import com.app.pustakam.core.drawing.color.DrawPaletteCatalog
import com.app.pustakam.core.drawing.model.DrawColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawColorTest {

    @Test
    fun parsesEveryHexForm() {
        assertEquals(DrawColor(0xFFFF0000L), DrawColor.fromHex("#F00"))
        assertEquals(DrawColor(0xFF2B2723L), DrawColor.fromHex("#2B2723"))
        assertEquals(DrawColor(0x802B2723L), DrawColor.fromHex("802b2723"))
        assertEquals(DrawColor(0x88FF0000L), DrawColor.fromHex("#8F00"))
        assertNull(DrawColor.fromHex("#12"))
        assertNull(DrawColor.fromHex("#GGGGGG"))
        assertNull(DrawColor.fromHex(""))
    }

    @Test
    fun formatsHexWithAndWithoutAlpha() {
        assertEquals("#2B2723", DrawColor(0xFF2B2723L).hex())
        assertEquals("#802B2723", DrawColor(0x802B2723L).hex())
        assertEquals("#FF2B2723", DrawColor(0xFF2B2723L).hexWithAlpha())
    }

    @Test
    fun hslRoundTripKeepsTheColor() {
        listOf("#E9A33C", "#3D6FB4", "#2E8B72", "#000000", "#FFFFFF", "#808080").forEach { hex ->
            val color = DrawColor.fromHex(hex)!!
            val hsl = color.hsl()
            val back = DrawColor.fromHsl(hsl.hue, hsl.saturation, hsl.lightness, hsl.opacity)
            assertTrue(color.isSimilarTo(back, 3), "$hex came back as ${back.hex()}")
        }
    }

    @Test
    fun hsvRoundTripKeepsTheColor() {
        listOf("#D9662F", "#8E5AA8", "#7A8B2E", "#123456").forEach { hex ->
            val color = DrawColor.fromHex(hex)!!
            val hsv = color.hsv()
            val back = DrawColor.fromHsv(hsv.hue, hsv.saturation, hsv.value, hsv.opacity)
            assertTrue(color.isSimilarTo(back, 3), "$hex came back as ${back.hex()}")
        }
    }

    @Test
    fun primaryHuesLandInTheirSectors() {
        assertEquals(0f, DrawColor.fromRgb(255, 0, 0).hsl().hue, 0.5f)
        assertEquals(120f, DrawColor.fromRgb(0, 255, 0).hsl().hue, 0.5f)
        assertEquals(240f, DrawColor.fromRgb(0, 0, 255).hsl().hue, 0.5f)
    }

    @Test
    fun opacityChangesOnlyAlpha() {
        val color = DrawColor.fromRgb(10, 20, 30).withOpacity(0.5f)
        assertEquals(128, color.alpha)
        assertEquals(10, color.red)
        assertEquals(20, color.green)
        assertEquals(30, color.blue)
    }

    @Test
    fun argbIntRoundTripsThroughNegativeInts() {
        val color = DrawColor(0xFFEE1122L)
        assertEquals(color, DrawColor.fromArgbInt(color.argbInt))
    }

    @Test
    fun recentColorsAreDistinctNewestFirstAndBounded() {
        var colors = DrawColorState.defaults()
        repeat(20) { colors = colors.used(DrawColor.fromRgb(it, 0, 0)) }
        colors = colors.used(DrawColor.fromRgb(5, 0, 0))
        assertEquals(DrawColorState.MAX_RECENT, colors.recent.size)
        assertEquals(DrawColor.fromRgb(5, 0, 0), colors.recent.first())
        assertEquals(colors.recent.distinct(), colors.recent)
    }

    @Test
    fun favoritesToggle() {
        val red = DrawColor.fromRgb(255, 0, 0)
        val once = DrawColorState.defaults().toggledFavorite(red)
        assertTrue(once.isFavorite(red))
        assertFalse(once.toggledFavorite(red).isFavorite(red))
    }

    @Test
    fun builtInPalettesAreReadOnlyAndCustomOnesEditable() {
        val defaults = DrawColorState.defaults()
        val ink = defaults.paletteById(DrawPaletteCatalog.INK_PALETTE_ID)!!
        assertEquals(10, ink.colors.size)
        assertEquals(ink, ink.withColor(DrawColor.WHITE))
        val custom = DrawPaletteCatalog.custom("mine", "Mine", listOf(DrawColor.BLACK))
        val grown = defaults.withPalette(custom).updatingPalette("mine") { it.withColor(DrawColor.WHITE) }
        assertEquals(listOf(DrawColor.BLACK, DrawColor.WHITE), grown.paletteById("mine")!!.colors)
        assertEquals(defaults.palettes.size, grown.withoutPalette("mine").palettes.size)
        assertEquals(defaults.palettes.size, defaults.withoutPalette(DrawPaletteCatalog.INK_PALETTE_ID).palettes.size)
    }
}
