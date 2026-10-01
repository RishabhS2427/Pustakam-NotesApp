package com.app.pustakam.core.drawing.color

import com.app.pustakam.core.drawing.model.DrawColor

data class DrawPalette(
    val id: String,
    val name: String,
    val colors: List<DrawColor>,
    val editable: Boolean
) {
    fun withColor(color: DrawColor): DrawPalette =
        if (!editable || color in colors) this else copy(colors = colors + color)

    fun withoutColor(color: DrawColor): DrawPalette =
        if (!editable) this else copy(colors = colors - color)

    fun renamed(value: String): DrawPalette = if (!editable) this else copy(name = value)
}

data class DrawColorState(
    val current: DrawColor,
    val recent: List<DrawColor>,
    val favorites: List<DrawColor>,
    val palettes: List<DrawPalette>
) {
    fun withCurrent(color: DrawColor): DrawColorState = copy(current = color)

    fun used(color: DrawColor): DrawColorState =
        copy(recent = (listOf(color) + recent.filterNot { it == color }).take(MAX_RECENT))

    fun isFavorite(color: DrawColor): Boolean = color in favorites

    fun toggledFavorite(color: DrawColor): DrawColorState =
        copy(favorites = if (color in favorites) favorites - color else (favorites + color).takeLast(MAX_FAVORITES))

    fun withPalette(palette: DrawPalette): DrawColorState =
        copy(palettes = palettes.filterNot { it.id == palette.id } + palette)

    fun withoutPalette(paletteId: String): DrawColorState =
        copy(palettes = palettes.filterNot { it.id == paletteId && it.editable })

    fun updatingPalette(paletteId: String, transform: (DrawPalette) -> DrawPalette): DrawColorState =
        copy(palettes = palettes.map { if (it.id == paletteId) transform(it) else it })

    fun paletteById(paletteId: String): DrawPalette? = palettes.firstOrNull { it.id == paletteId }

    companion object {
        const val MAX_RECENT = 12
        const val MAX_FAVORITES = 24

        fun defaults(): DrawColorState = DrawColorState(
            current = DrawPaletteCatalog.defaultInk(),
            recent = emptyList(),
            favorites = emptyList(),
            palettes = DrawPaletteCatalog.defaults()
        )
    }
}
