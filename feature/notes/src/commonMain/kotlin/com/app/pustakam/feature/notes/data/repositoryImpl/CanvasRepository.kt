package com.app.pustakam.feature.notes.data.repositoryImpl

import com.app.pustakam.core.common.coroutines.provideDispatcher
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.ErrorMessage
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.database.NotesDatabase
import com.app.pustakam.core.database.localdb.database.CanvasDao
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.Viewport
import com.app.pustakam.core.richtext.master.presentation.CanvasCommands
import com.app.pustakam.feature.notes.domain.repository.ICanvasRepository
import com.app.pustakam.feature.notes.domain.repository.INoteSyncRepository
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class CanvasRepository : ICanvasRepository, KoinComponent {

    private val database by inject<NotesDatabase>()
    private val syncRepository by inject<INoteSyncRepository>()
    private val dao by lazy { CanvasDao(database) }

    private val dispatcher by lazy { provideDispatcher() }

    private suspend fun <T> onIo(block: () -> T?): Result<BaseResponse<T>, Error> =
        withContext(dispatcher.io) {
            runCatching { block() }.fold(
                onSuccess = {
                    Result.Success(BaseResponse(data = it, isFromDb = true, isSuccessful = true))
                },
                onFailure = {
                    Result.Error(ErrorMessage(it.message ?: "Canvas storage is unavailable."))
                }
            )
        }

    override suspend fun load(noteId: String) = onIo { CanvasDocument(dao.nodes(noteId)) }

    override suspend fun loadVisible(noteId: String, rect: CanvasRect) =
        onIo { CanvasDocument(dao.nodesIn(noteId, rect)) }

    override suspend fun save(noteId: String, node: CanvasNode) =
        onIo { dao.upsert(noteId, node); node }.also { publish(noteId) }

    override suspend fun saveAll(noteId: String, nodes: List<CanvasNode>) =
        onIo { dao.upsertAll(noteId, nodes); nodes }.also { publish(noteId) }

    override suspend fun move(nodeId: String, x: Float, y: Float) =
        onIo { dao.move(nodeId, x, y); true }

    override suspend fun resize(nodeId: String, width: Float, height: Float) =
        onIo { dao.resize(nodeId, width, height); true }

    override suspend fun raise(nodeId: String, z: Int) = onIo { dao.raise(nodeId, z); true }

    override suspend fun rename(nodeId: String, name: String) =
        onIo { dao.rename(nodeId, name); true }

    override suspend fun remove(nodeId: String) = onIo { dao.delete(nodeId); true }

    override suspend fun removeAll(noteId: String) =
        onIo { dao.deleteAll(noteId); true }.also { publish(noteId) }

    private suspend fun publish(noteId: String) {
        runCatching { syncRepository.publishCanvasNodes(noteId, dao.nodes(noteId)) }
    }

    override suspend fun count(noteId: String) = onIo { dao.count(noteId).toInt() }

    override suspend fun nodeForContent(contentId: String) = onIo { dao.nodeForContent(contentId) }

    override suspend fun pruneOrphans() = onIo { dao.pruneOrphans(); true }

    override suspend fun loadViewport(noteId: String) = onIo { dao.viewport(noteId) }

    override suspend fun saveViewport(noteId: String, viewport: Viewport) =
        onIo { dao.saveViewport(noteId, viewport); viewport }

    override suspend fun saveEdit(noteId: String, nodes: List<CanvasNode>, removedIds: List<String>) =
        onIo { dao.applyEdit(noteId, nodes, removedIds); true }.also { publish(noteId) }

    // 📐 all or nothing: a half-done upgrade would scale some canvases twice on the next launch
    override suspend fun upgradeLayouts(unitScale: Float, maxPaperWidth: Float) = onIo {
        var upgraded = 0
        database.transaction {
            dao.noteIdsWithCanvas().forEach { noteId ->
                val stored = dao.nodes(noteId)
                val next = CanvasCommands.upgradedLayout(stored, unitScale, maxPaperWidth)
                if (next != stored) {
                    dao.replaceAll(noteId, next)
                    upgraded++
                }
                if (unitScale != 1f) {
                    dao.viewport(noteId)?.let { viewport ->
                        dao.saveViewport(
                            noteId,
                            viewport.copy(
                                offsetX = viewport.offsetX * unitScale,
                                offsetY = viewport.offsetY * unitScale
                            )
                        )
                    }
                }
            }
        }
        upgraded
    }
}
