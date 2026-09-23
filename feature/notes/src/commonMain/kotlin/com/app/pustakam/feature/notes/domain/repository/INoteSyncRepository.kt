package com.app.pustakam.feature.notes.domain.repository

import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasNode
import kotlinx.coroutines.flow.Flow

interface INoteSyncRepository {

    fun observeContents(noteId: String): Flow<List<NoteContentModel>>

    fun observeCanvasNodes(noteId: String): Flow<List<CanvasNode>>

    fun publishContents(noteId: String, contents: List<NoteContentModel>)

    fun publishCanvasNodes(noteId: String, nodes: List<CanvasNode>)

    // 🔄 24-Sep-2026 — a canvas another device saved, for a master editor that is already open
    fun observeRemoteCanvas(noteId: String): Flow<List<CanvasNode>>

    fun publishRemoteCanvas(noteId: String, nodes: List<CanvasNode>)
}
