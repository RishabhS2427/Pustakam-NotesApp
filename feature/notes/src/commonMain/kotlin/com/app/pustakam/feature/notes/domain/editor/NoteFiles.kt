package com.app.pustakam.feature.notes.domain.editor

import com.app.pustakam.core.model.models.response.notes.NoteContentModel

object NoteFiles {

    fun pathsOf(content: NoteContentModel?): List<String> {
        val media = content as? NoteContentModel.MediaContent ?: return emptyList()
        return listOfNotNull(media.localPath, media.thumbnailPath).filter { it.isNotEmpty() }
    }

    fun pathsOfAll(contents: List<NoteContentModel>): List<String> = contents.flatMap(::pathsOf)

    fun discardable(trashed: Set<String>, live: List<NoteContentModel>): List<String> {
        val kept = pathsOfAll(live).toSet()
        return trashed.filterNot { it in kept }
    }
}
