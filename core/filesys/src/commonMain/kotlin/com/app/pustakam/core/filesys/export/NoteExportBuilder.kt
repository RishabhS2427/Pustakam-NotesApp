package com.app.pustakam.core.filesys.export

import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.isImage

enum class ExportBlockKind { TITLE, PARAGRAPH, IMAGE, FILE, LINK, LOCATION }

data class ExportBlock(
    val kind: ExportBlockKind,
    val text: String = "",          // TITLE / PARAGRAPH
    val path: String = "",          // IMAGE / FILE
    val caption: String = "",       // IMAGE
    val width: Int = 0,             // IMAGE (0 = unknown → renderers use a default box)
    val height: Int = 0,            // IMAGE
    val name: String = "",          // FILE
    val typeLabel: String = "",     // FILE
    val url: String = "",           // LINK
    val label: String = "",         // LOCATION
)

// 🔧 20-Jul-2026: export targets — ext + mime shared so save/share code isn't duplicated
enum class ExportFormat(val ext: String, val mime: String) {
    PDF(".pdf", "application/pdf"),
    IMAGE(".png", "image/png"),
    DOCX(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    companion object {
        fun fromId(id: String): ExportFormat = when (id.lowercase()) {
            "pdf" -> PDF
            "image", "png" -> IMAGE
            "docx" -> DOCX
            else -> PDF
        }
    }
}

object NoteExportBuilder {

    // 🔧 20-Jul-2026: Note → ordered export blocks (position order, same rules as the book reader)
    fun build(note: Note): List<ExportBlock> {
        val blocks = mutableListOf<ExportBlock>()
        blocks.add(ExportBlock(ExportBlockKind.TITLE, text = note.title?.takeIf { it.isNotBlank() } ?: "Untitled note"))
        note.contents.sortedBy { it.position }.forEach { content ->
            when (content) {
                is NoteContentModel.TextContent ->
                    if (content.text.isNotBlank()) blocks.add(ExportBlock(ExportBlockKind.PARAGRAPH, text = content.text))

                is NoteContentModel.MediaContent -> when {
                    content.type.isImage() ->
                        blocks.add(
                            ExportBlock(
                                ExportBlockKind.IMAGE,
                                path = content.localPath ?: content.url,
                                caption = content.title,
                                width = content.width, height = content.height,
                            )
                        )
                    else -> blocks.add(
                        ExportBlock(
                            ExportBlockKind.FILE,
                            name = content.title.ifBlank { defaultName(content) },
                            typeLabel = content.type.name,
                            path = content.localPath ?: content.url,
                        )
                    )
                }

                is NoteContentModel.Link -> blocks.add(ExportBlock(ExportBlockKind.LINK, url = content.url))

                is NoteContentModel.Location ->
                    blocks.add(
                        ExportBlock(
                            ExportBlockKind.LOCATION,
                            label = content.address?.takeIf { it.isNotBlank() }
                                ?: "${content.latitude}, ${content.longitude}"
                        )
                    )

                is NoteContentModel.Drawing -> Unit
            }
        }
        return blocks
    }

    // 🔧 20-Jul-2026: only IMAGE paths need bytes for DOCX embedding — platforms base64 these
    fun imagePaths(blocks: List<ExportBlock>): List<String> =
        blocks.filter { it.kind == ExportBlockKind.IMAGE }.map { it.path }.filter { it.isNotBlank() }

    private fun defaultName(media: NoteContentModel.MediaContent): String =
        media.localPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "${media.type.name}${media.type.getExt()}"
}
