package com.app.pustakam.core.model.models.response.notes
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.common.util.getCurrentTimestamp

// 🔧 C6: this factory is the ONLY place ids/timestamps are generated.
//       id logic UNCHANGED: timestamp+UUID via UniqueIdGenerator — created once, survives sync round-trips.
//       Timestamps stay String (platform formats may differ). 🔧 C4: positions are Double.
object NoteContentObjectHelper {

    fun createText(noteId: String, positionedAt: Double, text: String = ""): NoteContentModel.TextContent {
        val now = "${getCurrentTimestamp()}"
        return NoteContentModel.TextContent(
            id = UniqueIdGenerator.generateUniqueId(),
            noteId = noteId,
            position = positionedAt,
            createdAt = now,
            updatedAt = now,
            text = text                       // 🔧 was silently ignored before (Piece-5 bug fixed)
        )
    }

    fun createMedia(
        contentType: ContentType, noteId: String, positionedAt: Double,
        localPath: String = "", url: String = "", duration: Long = 0,
        timestamp: String? = null,            // null/empty = "now" (original semantics kept)
        title: String = "",                   // 🔧 C1: persisted per-item title
        mimeType: String = "",                // 🔧 C1: media metadata passthrough
        sizeBytes: Long = 0, width: Int = 0, height: Int = 0,
        thumbnailPath: String? = null,
    ): NoteContentModel.MediaContent {
        val time = if (timestamp.isNullOrEmpty()) "${getCurrentTimestamp()}" else timestamp
        return NoteContentModel.MediaContent(
            id = UniqueIdGenerator.generateUniqueId(),
            type = contentType,
            position = positionedAt,
            duration = duration,
            createdAt = time,
            updatedAt = time,
            noteId = noteId,
            localPath = localPath,
            url = url,
            title = title,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            thumbnailPath = thumbnailPath,
        )
    }

    fun createHyperLink(link: String = "", noteId: String, positionedAt: Double): NoteContentModel.Link {
        val now = "${getCurrentTimestamp()}"
        return NoteContentModel.Link(
            id = UniqueIdGenerator.generateUniqueId(),
            position = positionedAt,
            noteId = noteId,
            url = link,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun createDrawing(
        noteId: String,
        positionedAt: Double,
        surface: String,
        width: Int,
        height: Int,
        drawing: String,
        target: String
    ): NoteContentModel.Drawing {
        val now = "${getCurrentTimestamp()}"
        return NoteContentModel.Drawing(
            id = UniqueIdGenerator.generateUniqueId(),
            noteId = noteId,
            position = positionedAt,
            createdAt = now,
            updatedAt = now,
            surface = surface,
            width = width,
            height = height,
            drawing = drawing,
            target = target,
        )
    }

    fun createLocation(noteId: String, positionedAt: Double, lat: Double = 0.0, long: Double = 0.0): NoteContentModel.Location {
        val now = "${getCurrentTimestamp()}"
        return NoteContentModel.Location(
            id = UniqueIdGenerator.generateUniqueId(),
            noteId = noteId,
            position = positionedAt,
            latitude = lat,
            longitude = long,
            createdAt = now,
            updatedAt = now,
        )
    }
}

// 🔧 15-Jul-2026 Phase 2.1: hard cap for ONE text block. Keeping blocks paragraph-sized means a
//   keystroke only ever copies a few KB no matter how long the whole note is (1000+ pages stay
//   editable). Oversized blocks are split ON SAVE — never while the user is typing.
const val MAX_TEXT_BLOCK_CHARS = 4000

// 🔧 15-Jul-2026 Phase 2.1: splits oversized plain-text blocks into MAX_TEXT_BLOCK_CHARS chunks.
//   • split points prefer the last newline in range, then the last space, then a hard cut
//   • the FIRST chunk keeps the original block's id (an edit, not a delete+create — sync-safe)
//   • follow-up chunks are new TextContent blocks at fractional positions between the original
//     block and whatever comes next (Double positions make this free — no renumbering)
//   • blocks with rich-text metadata spans are SKIPPED: span ranges index into the text and
//     would be corrupted by a split
//   Usage (on save): TextBlockSplitter.splitOversized(contents)?.let { result ->
//       contents = result.contents; markDirty(result.changedIds) }
object TextBlockSplitter {

    data class SplitResult(
        val contents: List<NoteContentModel>,
        /** ids of blocks that were changed or newly created — callers mark these dirty */
        val changedIds: Set<String>,
    )

    /** @return null when nothing needed splitting (the common case — zero-cost on save) */
    fun splitOversized(contents: List<NoteContentModel>): SplitResult? {
        if (contents.none { it.needsSplit() }) return null
        val sorted = contents.sortedBy { it.position }
        val result = mutableListOf<NoteContentModel>()
        val changedIds = mutableSetOf<String>()
        sorted.forEachIndexed { index, content ->
            if (!content.needsSplit()) {
                result.add(content)
                return@forEachIndexed
            }
            content as NoteContentModel.TextContent
            val chunks = chunkText(content.text)
            // position window: between this block and the next one (or +1.0 when it's last)
            val nextPosition = sorted.getOrNull(index + 1)?.position ?: (content.position + 1.0)
            val step = (nextPosition - content.position) / chunks.size
            chunks.forEachIndexed { chunkIndex, chunk ->
                if (chunkIndex == 0) {
                    val updated = content.withText(chunk)   // keeps the original id
                    result.add(updated)
                    changedIds.add(updated.id)
                } else {
                    val created = NoteContentObjectHelper.createText(
                        noteId = content.noteId,
                        positionedAt = content.position + (step * chunkIndex),
                        text = chunk
                    )
                    result.add(created)
                    changedIds.add(created.id)
                }
            }
        }
        return SplitResult(contents = result, changedIds = changedIds)
    }

    private fun NoteContentModel.needsSplit(): Boolean =
        this is NoteContentModel.TextContent &&
                text.length > MAX_TEXT_BLOCK_CHARS &&
                metadata?.spans.isNullOrEmpty()   // never split rich-text blocks (span ranges)

    private fun chunkText(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > MAX_TEXT_BLOCK_CHARS) {
            val window = remaining.substring(0, MAX_TEXT_BLOCK_CHARS)
            // prefer paragraph, then word boundary; require a sane minimum so a boundary at
            // position 3 doesn't produce confetti chunks
            val cut = window.lastIndexOf('\n').takeIf { it > MAX_TEXT_BLOCK_CHARS / 2 }
                ?: window.lastIndexOf(' ').takeIf { it > MAX_TEXT_BLOCK_CHARS / 2 }
                ?: MAX_TEXT_BLOCK_CHARS
            chunks.add(remaining.substring(0, cut))
            remaining = remaining.substring(cut).trimStart('\n')
        }
        if (remaining.isNotEmpty()) chunks.add(remaining)
        return chunks
    }
}
