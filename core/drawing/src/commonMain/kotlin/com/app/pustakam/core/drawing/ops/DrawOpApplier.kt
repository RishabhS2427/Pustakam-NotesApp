package com.app.pustakam.core.drawing.ops

import com.app.pustakam.core.drawing.model.DrawDocument
import com.app.pustakam.core.drawing.model.DrawElement

object DrawOpApplier {

    fun apply(document: DrawDocument, op: DrawOp): DrawDocument {
        val applied = when (op) {
            is DrawOp.Add -> document.withElements(merged(document.elements, op.elements))
            is DrawOp.Remove -> document.withElements(without(document.elements, op.elements.map { it.id }.toSet()))
            is DrawOp.Replace -> document.withElements(
                merged(without(document.elements, op.before.map { it.id }.toSet()), op.after)
            )
            is DrawOp.Paper -> document.withPaper(op.after)
        }
        return applied.withClock(op.clock)
    }

    private fun without(elements: List<DrawElement>, ids: Set<String>): List<DrawElement> =
        if (ids.isEmpty()) elements else elements.filterNot { it.id in ids }

    private fun merged(elements: List<DrawElement>, incoming: List<DrawElement>): List<DrawElement> {
        if (incoming.isEmpty()) return elements
        val byId = LinkedHashMap<String, DrawElement>(elements.size + incoming.size)
        elements.forEach { byId[it.id] = it }
        incoming.forEach { element ->
            val existing = byId[element.id]
            if (existing == null || element.wins(existing) || existing == element) byId[element.id] = element
        }
        return byId.values.toList()
    }
}
