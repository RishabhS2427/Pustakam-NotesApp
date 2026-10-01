package com.app.pustakam.core.drawing.color

import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.richtext.presentation.SmartTextCatalog

object DrawPaletteCatalog {

    const val INK_PALETTE_ID = "palette-ink"

    const val HIGHLIGHT_PALETTE_ID = "palette-highlight"

    const val BASIC_PALETTE_ID = "palette-basic"

    private val BASIC = listOf(
        "#000000", "#FFFFFF", "#9E9E9E", "#E53935", "#FB8C00", "#FDD835",
        "#43A047", "#00897B", "#1E88E5", "#3949AB", "#8E24AA", "#D81B60", "#6D4C41"
    )

    private val PAPER = listOf("#FFFFFF", "#FBF6EE", "#F1F3F4", "#FFF8C5", "#E8F5E9", "#E3F2FD", "#2B2723", "#000000")

    fun paperColors(): List<DrawColor> = colorsOf(PAPER)

    fun defaultInk(): DrawColor = DrawColor.fromHex(SmartTextCatalog.textColors.last()) ?: DrawColor.BLACK

    fun defaults(): List<DrawPalette> = listOf(
        DrawPalette(INK_PALETTE_ID, "Ink", colorsOf(SmartTextCatalog.textColors), editable = false),
        DrawPalette(HIGHLIGHT_PALETTE_ID, "Highlight", colorsOf(SmartTextCatalog.highlightColors), editable = false),
        DrawPalette(BASIC_PALETTE_ID, "Basic", colorsOf(BASIC), editable = false)
    )

    fun custom(id: String, name: String, colors: List<DrawColor>): DrawPalette =
        DrawPalette(id, name, colors.distinct(), editable = true)

    private fun colorsOf(hexes: List<String>): List<DrawColor> = hexes.mapNotNull { DrawColor.fromHex(it) }
}
