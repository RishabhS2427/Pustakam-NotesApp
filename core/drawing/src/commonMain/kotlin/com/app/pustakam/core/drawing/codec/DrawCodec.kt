package com.app.pustakam.core.drawing.codec

import com.app.pustakam.core.drawing.brush.DrawBrushCatalog
import com.app.pustakam.core.drawing.color.DrawColorState
import com.app.pustakam.core.drawing.color.DrawPalette
import com.app.pustakam.core.drawing.model.DrawBlend
import com.app.pustakam.core.drawing.model.DrawBrush
import com.app.pustakam.core.drawing.model.DrawBrushKind
import com.app.pustakam.core.drawing.model.DrawColor
import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawElement
import com.app.pustakam.core.drawing.model.DrawElementKind
import com.app.pustakam.core.drawing.model.DrawLayer
import com.app.pustakam.core.drawing.model.DrawLayerKind
import com.app.pustakam.core.drawing.model.DrawOrientation
import com.app.pustakam.core.drawing.model.DrawPaper
import com.app.pustakam.core.drawing.model.DrawPaperSize
import com.app.pustakam.core.drawing.model.DrawPattern
import com.app.pustakam.core.drawing.model.DrawShapeKind
import com.app.pustakam.core.drawing.model.DrawShapeSpec
import com.app.pustakam.core.drawing.model.DrawSurface
import com.app.pustakam.core.drawing.model.DrawTexture
import com.app.pustakam.core.drawing.model.DrawUnit
import com.app.pustakam.core.drawing.ops.DrawOp
import com.app.pustakam.core.drawing.ops.DrawOpKind
import com.app.pustakam.core.drawing.tool.DrawToolSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

object DrawCodec {

    const val VERSION = 1

    fun version(): Int = VERSION

    fun encode(document: DrawDocument): String = documentJson(document).toString()

    fun decode(payload: String?, fallback: DrawDocument): DrawDocument {
        if (payload.isNullOrBlank()) return fallback
        return runCatching { documentOf(parse(payload), fallback) }.getOrNull() ?: fallback
    }

    fun encodeOp(op: DrawOp): String = opJson(op).toString()

    fun decodeOp(payload: String?): DrawOp? {
        if (payload.isNullOrBlank()) return null
        return runCatching { opOf(parse(payload)) }.getOrNull()
    }

    fun encodeToolbox(settings: DrawToolSettings, colors: DrawColorState): String = JsonObject(
        linkedMapOf(
            "v" to JsonPrimitive(VERSION),
            "brushKind" to JsonPrimitive(settings.brushKind.name),
            "brushes" to JsonArray(settings.brushes.map { brushJson(it) }),
            "eraserKind" to JsonPrimitive(settings.eraserKind.name),
            "eraserSize" to number(settings.eraserSize),
            "shapeKind" to JsonPrimitive(settings.shapeKind.name),
            "shapes" to JsonArray(settings.shapes.map { shapeJson(it) }),
            "stylusOnly" to JsonPrimitive(settings.stylusOnly),
            "current" to JsonPrimitive(colors.current.hexWithAlpha()),
            "recent" to JsonArray(colors.recent.map { JsonPrimitive(it.hexWithAlpha()) }),
            "favorites" to JsonArray(colors.favorites.map { JsonPrimitive(it.hexWithAlpha()) }),
            "palettes" to JsonArray(colors.palettes.filter { it.editable }.map { paletteJson(it) })
        )
    ).toString()

    fun decodeToolSettings(payload: String?, fallback: DrawToolSettings): DrawToolSettings {
        if (payload.isNullOrBlank()) return fallback
        return runCatching {
            val root = parse(payload)
            val brushes = root.array("brushes").mapNotNull { (it as? JsonObject)?.let { brush -> brushOf(brush) } }
            fallback.copy(
                brushKind = enumOf(root.string("brushKind"), fallback.brushKind),
                brushes = fallback.brushes.map { preset -> brushes.firstOrNull { it.kind == preset.kind } ?: preset },
                eraserKind = enumOf(root.string("eraserKind"), fallback.eraserKind),
                eraserSize = root.float("eraserSize", fallback.eraserSize),
                shapeKind = enumOf(root.string("shapeKind"), fallback.shapeKind),
                shapes = root.array("shapes").mapNotNull { (it as? JsonObject)?.let { shape -> shapeOf(shape) } }
                    .ifEmpty { fallback.shapes },
                stylusOnly = root.boolean("stylusOnly", fallback.stylusOnly)
            )
        }.getOrNull() ?: fallback
    }

    fun decodeColors(payload: String?, fallback: DrawColorState): DrawColorState {
        if (payload.isNullOrBlank()) return fallback
        return runCatching {
            val root = parse(payload)
            val custom = root.array("palettes").mapNotNull { (it as? JsonObject)?.let { palette -> paletteOf(palette) } }
            fallback.copy(
                current = colorOf(root.string("current")) ?: fallback.current,
                recent = root.array("recent").mapNotNull { colorOf((it as? JsonPrimitive)?.content) },
                favorites = root.array("favorites").mapNotNull { colorOf((it as? JsonPrimitive)?.content) },
                palettes = fallback.palettes.filterNot { it.editable } + custom
            )
        }.getOrNull() ?: fallback
    }

    private fun parse(payload: String): JsonObject = Json.parseToJsonElement(payload) as JsonObject

    private fun documentJson(document: DrawDocument): JsonObject = JsonObject(
        linkedMapOf(
            "v" to JsonPrimitive(VERSION),
            "id" to JsonPrimitive(document.id),
            "surface" to JsonPrimitive(document.surface.name),
            "clock" to JsonPrimitive(document.clock),
            "paper" to paperJson(document.paper),
            "layers" to JsonArray(document.layers.map { layerJson(it) }),
            "elements" to JsonArray(document.elements.map { elementJson(it) })
        )
    )

    private fun documentOf(root: JsonObject, fallback: DrawDocument): DrawDocument {
        val layers = root.array("layers").mapNotNull { (it as? JsonObject)?.let { layer -> layerOf(layer) } }
        return DrawDocument(
            id = root.string("id") ?: fallback.id,
            surface = enumOf(root.string("surface"), fallback.surface),
            paper = (root["paper"] as? JsonObject)?.let { paperOf(it, fallback.paper) } ?: fallback.paper,
            layers = layers.ifEmpty { fallback.layers.ifEmpty { listOf(DrawLayer.base()) } },
            elements = elementsOf(root.array("elements")),
            clock = root.long("clock", 0L)
        )
    }

    private fun elementsOf(array: List<JsonElement>): List<DrawElement> =
        array.mapNotNull { item -> (item as? JsonObject)?.let { runCatching { elementOf(it) }.getOrNull() } }

    private fun paperJson(paper: DrawPaper): JsonObject = JsonObject(
        linkedMapOf(
            "size" to JsonPrimitive(paper.size.name),
            "or" to JsonPrimitive(paper.orientation.name),
            "w" to number(paper.width),
            "h" to number(paper.height),
            "u" to JsonPrimitive(paper.unit.name),
            "dpi" to JsonPrimitive(paper.dpi),
            "bg" to JsonPrimitive(paper.background.hexWithAlpha()),
            "tr" to JsonPrimitive(paper.transparent),
            "pat" to JsonPrimitive(paper.pattern.name),
            "spc" to number(paper.spacing),
            "pc" to JsonPrimitive(paper.patternColor.hexWithAlpha()),
            "tex" to JsonPrimitive(paper.texture.name)
        )
    )

    private fun paperOf(json: JsonObject, fallback: DrawPaper): DrawPaper = DrawPaper(
        size = enumOf<DrawPaperSize>(json.string("size"), fallback.size),
        orientation = enumOf<DrawOrientation>(json.string("or"), fallback.orientation),
        width = json.float("w", fallback.width),
        height = json.float("h", fallback.height),
        unit = enumOf<DrawUnit>(json.string("u"), fallback.unit),
        dpi = json.int("dpi", fallback.dpi),
        background = colorOf(json.string("bg")) ?: fallback.background,
        transparent = json.boolean("tr", fallback.transparent),
        pattern = enumOf<DrawPattern>(json.string("pat"), fallback.pattern),
        spacing = json.float("spc", fallback.spacing),
        patternColor = colorOf(json.string("pc")) ?: fallback.patternColor,
        texture = enumOf<DrawTexture>(json.string("tex"), fallback.texture)
    )

    private fun layerJson(layer: DrawLayer): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(layer.id),
            "n" to JsonPrimitive(layer.name),
            "k" to JsonPrimitive(layer.kind.name),
            "vis" to JsonPrimitive(layer.visible),
            "lk" to JsonPrimitive(layer.locked),
            "op" to number(layer.opacity),
            "bl" to JsonPrimitive(layer.blend.name),
            "o" to JsonPrimitive(layer.order)
        )
    )

    private fun layerOf(json: JsonObject): DrawLayer? {
        val id = json.string("id") ?: return null
        val base = DrawLayer.base()
        return DrawLayer(
            id = id,
            name = json.string("n") ?: base.name,
            kind = enumOf<DrawLayerKind>(json.string("k"), base.kind),
            visible = json.boolean("vis", true),
            locked = json.boolean("lk", false),
            opacity = json.float("op", 1f),
            blend = enumOf<DrawBlend>(json.string("bl"), DrawBlend.NORMAL),
            order = json.double("o", 0.0)
        )
    }

    private fun elementJson(element: DrawElement): JsonObject {
        val fields = linkedMapOf<String, JsonElement>(
            "id" to JsonPrimitive(element.id),
            "k" to JsonPrimitive(element.kind.name),
            "l" to JsonPrimitive(element.layerId),
            "o" to JsonPrimitive(element.order),
            "au" to JsonPrimitive(element.author),
            "c" to JsonPrimitive(element.clock),
            "s" to JsonPrimitive(element.seed),
            "col" to JsonPrimitive(element.color.hexWithAlpha()),
            "b" to brushJson(element.brush),
            "p" to JsonPrimitive(DrawPointCodec.encode(element.points))
        )
        element.anchorId?.let { fields["a"] = JsonPrimitive(it) }
        if (element.kind == DrawElementKind.SHAPE) fields["sh"] = shapeJson(element.shape)
        return JsonObject(fields)
    }

    private fun elementOf(json: JsonObject): DrawElement? {
        val id = json.string("id") ?: return null
        val kind = enumOf(json.string("k"), DrawElementKind.STROKE)
        return DrawElement(
            id = id,
            kind = kind,
            layerId = json.string("l") ?: DrawLayer.BASE_ID,
            anchorId = json.string("a"),
            points = DrawPointCodec.decode(json.string("p").orEmpty()),
            brush = (json["b"] as? JsonObject)?.let { brushOf(it) } ?: DrawBrushCatalog.defaultBrush(DrawBrushKind.BALLPOINT),
            color = colorOf(json.string("col")) ?: DrawColor.BLACK,
            shape = if (kind == DrawElementKind.SHAPE) {
                (json["sh"] as? JsonObject)?.let { shapeOf(it) } ?: DrawShapeSpec.none()
            } else {
                DrawShapeSpec.none()
            },
            order = json.double("o", 0.0),
            author = json.string("au").orEmpty(),
            clock = json.long("c", 0L),
            seed = json.int("s", 0)
        )
    }

    private fun brushJson(brush: DrawBrush): JsonObject = JsonObject(
        linkedMapOf(
            "k" to JsonPrimitive(brush.kind.name),
            "sz" to number(brush.size),
            "op" to number(brush.opacity),
            "fl" to number(brush.flow),
            "hd" to number(brush.hardness),
            "sp" to number(brush.spacing),
            "sm" to number(brush.smoothing),
            "ps" to number(brush.pressureSensitivity),
            "ts" to number(brush.tiltSensitivity),
            "vs" to number(brush.velocitySensitivity)
        )
    )

    private fun brushOf(json: JsonObject): DrawBrush {
        val fallback = DrawBrushCatalog.defaultBrush(enumOf(json.string("k"), DrawBrushKind.BALLPOINT))
        return DrawBrush(
            kind = fallback.kind,
            size = json.float("sz", fallback.size),
            opacity = json.float("op", fallback.opacity),
            flow = json.float("fl", fallback.flow),
            hardness = json.float("hd", fallback.hardness),
            spacing = json.float("sp", fallback.spacing),
            smoothing = json.float("sm", fallback.smoothing),
            pressureSensitivity = json.float("ps", fallback.pressureSensitivity),
            tiltSensitivity = json.float("ts", fallback.tiltSensitivity),
            velocitySensitivity = json.float("vs", fallback.velocitySensitivity)
        )
    }

    private fun shapeJson(shape: DrawShapeSpec): JsonObject = JsonObject(
        linkedMapOf(
            "k" to JsonPrimitive(shape.kind.name),
            "n" to JsonPrimitive(shape.sides),
            "ir" to number(shape.innerRatio),
            "cr" to number(shape.cornerRadius),
            "sw" to number(shape.sweep),
            "f" to JsonPrimitive(shape.filled)
        )
    )

    private fun shapeOf(json: JsonObject): DrawShapeSpec {
        val fallback = DrawShapeSpec.of(enumOf(json.string("k"), DrawShapeKind.NONE))
        return DrawShapeSpec(
            kind = fallback.kind,
            sides = json.int("n", fallback.sides),
            innerRatio = json.float("ir", fallback.innerRatio),
            cornerRadius = json.float("cr", fallback.cornerRadius),
            sweep = json.float("sw", fallback.sweep),
            filled = json.boolean("f", fallback.filled)
        )
    }

    private fun paletteJson(palette: DrawPalette): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(palette.id),
            "n" to JsonPrimitive(palette.name),
            "c" to JsonArray(palette.colors.map { JsonPrimitive(it.hexWithAlpha()) })
        )
    )

    private fun paletteOf(json: JsonObject): DrawPalette? {
        val id = json.string("id") ?: return null
        return DrawPalette(
            id = id,
            name = json.string("n").orEmpty(),
            colors = json.array("c").mapNotNull { colorOf((it as? JsonPrimitive)?.content) },
            editable = true
        )
    }

    private fun opJson(op: DrawOp): JsonObject {
        val fields = linkedMapOf<String, JsonElement>(
            "t" to JsonPrimitive(op.kind.name),
            "au" to JsonPrimitive(op.author),
            "c" to JsonPrimitive(op.clock)
        )
        when (op) {
            is DrawOp.Add -> fields["e"] = JsonArray(op.elements.map { elementJson(it) })
            is DrawOp.Remove -> fields["e"] = JsonArray(op.elements.map { elementJson(it) })
            is DrawOp.Replace -> {
                fields["b"] = JsonArray(op.before.map { elementJson(it) })
                fields["a"] = JsonArray(op.after.map { elementJson(it) })
            }
            is DrawOp.Paper -> {
                fields["pb"] = paperJson(op.before)
                fields["pa"] = paperJson(op.after)
            }
        }
        return JsonObject(fields)
    }

    private fun opOf(json: JsonObject): DrawOp? {
        val author = json.string("au").orEmpty()
        val clock = json.long("c", 0L)
        return when (enumOrNull<DrawOpKind>(json.string("t"))) {
            DrawOpKind.ADD -> DrawOp.Add(elementsOf(json.array("e")), author, clock)
            DrawOpKind.REMOVE -> DrawOp.Remove(elementsOf(json.array("e")), author, clock)
            DrawOpKind.REPLACE -> DrawOp.Replace(elementsOf(json.array("b")), elementsOf(json.array("a")), author, clock)
            DrawOpKind.PAPER -> {
                val fallback = DrawPaper.infinite()
                val before = (json["pb"] as? JsonObject)?.let { paperOf(it, fallback) } ?: return null
                val after = (json["pa"] as? JsonObject)?.let { paperOf(it, fallback) } ?: return null
                DrawOp.Paper(before, after, author, clock)
            }
            null -> null
        }
    }

    private fun number(value: Float): JsonPrimitive = JsonPrimitive(if (value.isFinite()) value else 0f)

    private fun colorOf(hex: String?): DrawColor? = hex?.let { DrawColor.fromHex(it) }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T = enumOrNull<T>(name) ?: fallback

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

    private fun JsonObject.float(key: String, fallback: Float): Float =
        primitive(key)?.floatOrNull?.takeIf { it.isFinite() } ?: fallback

    private fun JsonObject.double(key: String, fallback: Double): Double =
        primitive(key)?.doubleOrNull?.takeIf { it.isFinite() } ?: fallback

    private fun JsonObject.int(key: String, fallback: Int): Int = primitive(key)?.intOrNull ?: fallback

    private fun JsonObject.long(key: String, fallback: Long): Long = primitive(key)?.longOrNull ?: fallback

    private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean = primitive(key)?.booleanOrNull ?: fallback

    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()
}
