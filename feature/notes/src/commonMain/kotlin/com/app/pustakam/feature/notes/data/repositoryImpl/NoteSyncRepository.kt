package com.app.pustakam.feature.notes.data.repositoryImpl

import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.feature.notes.domain.repository.INoteSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent

internal class NoteSyncRepository : INoteSyncRepository, KoinComponent {

    private data class ContentsEvent(val noteId: String, val contents: List<NoteContentModel>)

    private data class NodesEvent(val noteId: String, val nodes: List<CanvasNode>)

    private val contents = MutableSharedFlow<ContentsEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    private val nodes = MutableSharedFlow<NodesEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    private val remoteCanvas = MutableSharedFlow<NodesEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    override fun observeContents(noteId: String): Flow<List<NoteContentModel>> =
        contents.filter { it.noteId == noteId }.map { it.contents }

    override fun observeCanvasNodes(noteId: String): Flow<List<CanvasNode>> =
        nodes.filter { it.noteId == noteId }.map { it.nodes }

    override fun publishContents(noteId: String, contents: List<NoteContentModel>) {
        if (noteId.isEmpty()) return
        this.contents.tryEmit(ContentsEvent(noteId, contents))
    }

    override fun publishCanvasNodes(noteId: String, nodes: List<CanvasNode>) {
        if (noteId.isEmpty()) return
        this.nodes.tryEmit(NodesEvent(noteId, nodes))
    }

    override fun observeRemoteCanvas(noteId: String): Flow<List<CanvasNode>> =
        remoteCanvas.filter { it.noteId == noteId }.map { it.nodes }

    override fun publishRemoteCanvas(noteId: String, nodes: List<CanvasNode>) {
        if (noteId.isEmpty() || nodes.isEmpty()) return
        remoteCanvas.tryEmit(NodesEvent(noteId, nodes))
    }
}
