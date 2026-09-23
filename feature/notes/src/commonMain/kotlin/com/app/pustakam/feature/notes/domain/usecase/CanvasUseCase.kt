package com.app.pustakam.feature.notes.domain.usecase

import com.app.pustakam.core.data.usecases.BaseUseCase
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.CanvasRect
import com.app.pustakam.core.richtext.master.model.Viewport
import com.app.pustakam.feature.notes.domain.repository.ICanvasRepository
import org.koin.core.component.inject

abstract class CanvasBaseUseCase : BaseUseCase() {
    protected val canvasRepository: ICanvasRepository by inject()
}

class ReadCanvasUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String) =
        getBaseApiCall { canvasRepository.load(noteId) }
}

class ReadVisibleCanvasUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String, rect: CanvasRect) =
        getBaseApiCall { canvasRepository.loadVisible(noteId, rect) }
}

class ReadCanvasViewportUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String) =
        getBaseApiCall { canvasRepository.loadViewport(noteId) }
}

class SaveCanvasNodeUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String, node: CanvasNode) =
        getBaseApiCall { canvasRepository.save(noteId, node) }
}

class SaveCanvasNodesUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String, nodes: List<CanvasNode>) =
        getBaseApiCall { canvasRepository.saveAll(noteId, nodes) }
}

class MoveCanvasNodeUseCase : CanvasBaseUseCase() {
     operator fun invoke(nodeId: String, x: Float, y: Float) =
        getBaseApiCall { canvasRepository.move(nodeId, x, y) }
}

class ResizeCanvasNodeUseCase : CanvasBaseUseCase() {
     operator fun invoke(nodeId: String, width: Float, height: Float) =
        getBaseApiCall { canvasRepository.resize(nodeId, width, height) }
}

class RenameCanvasNodeUseCase : CanvasBaseUseCase() {
     operator fun invoke(nodeId: String, name: String) =
        getBaseApiCall { canvasRepository.rename(nodeId, name) }
}

class RemoveCanvasNodeUseCase : CanvasBaseUseCase() {
     operator fun invoke(nodeId: String) =
        getBaseApiCall { canvasRepository.remove(nodeId) }
}

class ClearCanvasUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String) =
        getBaseApiCall { canvasRepository.removeAll(noteId) }
}

class SaveCanvasViewportUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String, viewport: Viewport) =
        getBaseApiCall { canvasRepository.saveViewport(noteId, viewport) }
}

class SaveCanvasEditUseCase : CanvasBaseUseCase() {
     operator fun invoke(noteId: String, nodes: List<CanvasNode>, removedIds: List<String>) =
        getBaseApiCall { canvasRepository.saveEdit(noteId, nodes, removedIds) }
}

class UpgradeCanvasLayoutsUseCase : CanvasBaseUseCase() {
     operator fun invoke(unitScale: Float, maxPaperWidth: Float) =
        getBaseApiCall { canvasRepository.upgradeLayouts(unitScale, maxPaperWidth) }
}

class PruneCanvasOrphansUseCase : CanvasBaseUseCase() {
     operator fun invoke() =
        getBaseApiCall { canvasRepository.pruneOrphans() }
}
