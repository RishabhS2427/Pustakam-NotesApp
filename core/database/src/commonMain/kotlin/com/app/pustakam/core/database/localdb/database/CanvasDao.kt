package com.app.pustakam.core.database.localdb.database

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.database.NotesDatabase
import com.app.pustakam.core.model.models.response.notes.NoteCanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.CanvasRole
import com.app.pustakam.core.richtext.master.model.Viewport

class CanvasDao(private val database: NotesDatabase) {

    private val queries get() = database.notesDatabaseQueries

    fun upsert(noteId: String, node: CanvasNode) {
        queries.upsertCanvasNode(
            id = node.id,
            noteId = noteId,
            kind = node.kind.name,
            name = node.name,
            contentId = node.contentId,
            parentId = node.parentId,
            x = node.rect.x.toDouble(),
            y = node.rect.y.toDouble(),
            width = node.rect.width.toDouble(),
            height = node.rect.height.toDouble(),
            z = node.z.toLong(),
            locked = if (node.locked) 1L else 0L,
            hidden = if (node.hidden) 1L else 0L,
            links = node.links.joinToString(LINK_SEPARATOR),
            role = node.role.name,
            slotOrder = node.slotOrder,
            pageOrder = node.pageOrder,
            updatedAt = getCurrentTimestamp().toString()
        )
    }

    fun upsertAll(noteId: String, nodes: List<CanvasNode>) {
        queries.transaction { nodes.forEach { upsert(noteId, it) } }
    }

    fun delete(nodeId: String) = queries.deleteCanvasNode(nodeId)

    // 🔄 24-Sep-2026 — one user edit is one transaction: rows written, rows deleted, and a clean note put back on the push queue so no pull can overwrite the edit before the note is stamped
    fun applyEdit(noteId: String, nodes: List<CanvasNode>, removedIds: List<String>) {
        queries.transaction {
            nodes.forEach { upsert(noteId, it) }
            removedIds.forEach { queries.deleteCanvasNode(it) }
            queries.markNoteDirtyForMedia(noteId)
        }
    }

    fun replaceAll(noteId: String, nodes: List<CanvasNode>) {
        queries.transaction {
            queries.deleteCanvasNodesForNote(noteId)
            nodes.forEach { upsert(noteId, it) }
        }
    }

    fun noteIdsWithCanvas(): List<String> = queries.selectCanvasNoteIds().executeAsList()

    // 🔄 24-Sep-2026 — the canvas as it travels inside its note
    fun wireNodes(noteId: String): List<NoteCanvasNode> =
        queries.selectCanvasNodes(noteId).executeAsList().map { row ->
            NoteCanvasNode(
                id = row.id,
                kind = row.kind,
                role = row.role,
                name = row.name,
                contentId = row.contentId,
                parentId = row.parentId,
                x = row.x.toFloat(),
                y = row.y.toFloat(),
                width = row.width.toFloat(),
                height = row.height.toFloat(),
                z = row.z.toInt(),
                locked = row.locked == 1L,
                hidden = row.hidden == 1L,
                links = if (row.links.isEmpty()) emptyList() else row.links.split(LINK_SEPARATOR),
                slotOrder = row.slotOrder,
                pageOrder = row.pageOrder
            )
        }

    // 🔄 24-Sep-2026 — a pulled canvas replaces this device's rows for the note; the viewport stays this device's own
    fun replaceFromWire(noteId: String, nodes: List<NoteCanvasNode>) {
        val stamp = getCurrentTimestamp().toString()
        queries.transaction {
            queries.deleteCanvasNodesForNote(noteId)
            nodes.forEach { node ->
                queries.upsertCanvasNode(
                    id = node.id,
                    noteId = noteId,
                    kind = node.kind,
                    name = node.name,
                    contentId = node.contentId,
                    parentId = node.parentId,
                    x = node.x.toDouble(),
                    y = node.y.toDouble(),
                    width = node.width.toDouble(),
                    height = node.height.toDouble(),
                    z = node.z.toLong(),
                    locked = if (node.locked) 1L else 0L,
                    hidden = if (node.hidden) 1L else 0L,
                    links = node.links.joinToString(LINK_SEPARATOR),
                    role = node.role,
                    slotOrder = node.slotOrder,
                    pageOrder = node.pageOrder,
                    updatedAt = stamp
                )
            }
        }
    }

    fun deleteAll(noteId: String) = queries.deleteCanvasNodesForNote(noteId)

    fun nodes(noteId: String): List<CanvasNode> =
        queries.selectCanvasNodes(noteId).executeAsList().map { it.toNode() }

    fun nodesIn(noteId: String, rect: CanvasRect): List<CanvasNode> =
        queries.selectCanvasNodesInRect(
            noteId = noteId,
            right = rect.right.toDouble(),
            left = rect.x.toDouble(),
            bottom = rect.bottom.toDouble(),
            top = rect.y.toDouble()
        ).executeAsList().map { it.toNode() }

    fun count(noteId: String): Long = queries.countCanvasNodes(noteId).executeAsOne()

    fun nodeForContent(contentId: String): CanvasNode? =
        queries.selectCanvasNodeForContent(contentId).executeAsOneOrNull()?.toNode()

    fun pruneOrphans() = queries.deleteOrphanCanvasNodes()

    fun move(nodeId: String, x: Float, y: Float) =
        queries.moveCanvasNode(x.toDouble(), y.toDouble(), getCurrentTimestamp().toString(), nodeId)

    fun resize(nodeId: String, width: Float, height: Float) =
        queries.resizeCanvasNode(
            width.toDouble(),
            height.toDouble(),
            getCurrentTimestamp().toString(),
            nodeId
        )

    fun raise(nodeId: String, z: Int) =
        queries.raiseCanvasNode(z.toLong(), getCurrentTimestamp().toString(), nodeId)

    fun rename(nodeId: String, name: String) =
        queries.renameCanvasNode(name, getCurrentTimestamp().toString(), nodeId)

    fun saveViewport(noteId: String, viewport: Viewport) {
        queries.upsertCanvasViewport(
            noteId = noteId,
            offsetX = viewport.offsetX.toDouble(),
            offsetY = viewport.offsetY.toDouble(),
            scale = viewport.scale.toDouble(),
            updatedAt = getCurrentTimestamp().toString()
        )
    }

    fun viewport(noteId: String): Viewport? =
        queries.selectCanvasViewport(noteId).executeAsOneOrNull()?.let {
            Viewport(
                offsetX = it.offsetX.toFloat(),
                offsetY = it.offsetY.toFloat(),
                scale = it.scale.toFloat()
            )
        }

    private fun com.app.pustakam.core.database.CanvasNodeEntity.toNode(): CanvasNode = CanvasNode(
        id = id,
        kind = runCatching { ContentType.valueOf(kind) }
            .getOrDefault(ContentType.TEXT),
        name = name,
        rect = CanvasRect(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()),
        z = z.toInt(),
        contentId = contentId,
        parentId = parentId,
        locked = locked == 1L,
        hidden = hidden == 1L,
        links = if (links.isEmpty()) emptyList() else links.split(LINK_SEPARATOR),
        // an unknown value can only mean a row written by a newer build; treat it as a widget
        role = runCatching { CanvasRole.valueOf(role) }.getOrDefault(CanvasRole.WIDGET),
        slotOrder = slotOrder,
        pageOrder = pageOrder
    )

    companion object {
        private const val LINK_SEPARATOR = ","
    }
}

// 🔄 24-Sep-2026 — what a pulled canvas node becomes on this device, for an editor that is already open
fun NoteCanvasNode.toCanvasNode(): CanvasNode = CanvasNode(
    id = id,
    kind = runCatching { ContentType.valueOf(kind) }.getOrDefault(ContentType.TEXT),
    name = name,
    rect = CanvasRect(x, y, width, height),
    z = z,
    contentId = contentId,
    parentId = parentId,
    locked = locked,
    hidden = hidden,
    links = links,
    role = runCatching { CanvasRole.valueOf(role) }.getOrDefault(CanvasRole.WIDGET),
    slotOrder = slotOrder,
    pageOrder = pageOrder
)
