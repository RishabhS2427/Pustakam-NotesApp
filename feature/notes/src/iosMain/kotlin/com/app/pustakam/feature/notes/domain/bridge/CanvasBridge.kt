package com.app.pustakam.feature.notes.domain.bridge

import com.app.pustakam.core.common.bridge.BridgeError
import com.app.pustakam.core.common.bridge.Closeable
import com.app.pustakam.core.data.bridge.subscribeTo
import com.app.pustakam.core.data.bridge.watch
import com.app.pustakam.core.richtext.master.model.CanvasDocument
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.core.richtext.master.model.Viewport
import com.app.pustakam.feature.notes.domain.usecase.ClearCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.MoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveRemoteCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.PruneCanvasOrphansUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadCanvasViewportUseCase
import com.app.pustakam.feature.notes.domain.usecase.RemoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.RenameCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.ResizeCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasEditUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasNodesUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasViewportUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpgradeCanvasLayoutsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The ONLY Canvas entry point for iOS, built like [NotesBridge]. One instance per Swift
 * ViewModel; scope is Main so callbacks land on the main thread, while the use cases still
 * do their work on IO via getBaseApiCall().
 */
class CanvasBridge : KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private companion object {
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private val readCanvasUseCase: ReadCanvasUseCase by inject()
    private val readCanvasViewportUseCase: ReadCanvasViewportUseCase by inject()
    private val saveCanvasNodeUseCase: SaveCanvasNodeUseCase by inject()
    private val saveCanvasNodesUseCase: SaveCanvasNodesUseCase by inject()
    private val moveCanvasNodeUseCase: MoveCanvasNodeUseCase by inject()
    private val resizeCanvasNodeUseCase: ResizeCanvasNodeUseCase by inject()
    private val renameCanvasNodeUseCase: RenameCanvasNodeUseCase by inject()
    private val removeCanvasNodeUseCase: RemoveCanvasNodeUseCase by inject()
    private val clearCanvasUseCase: ClearCanvasUseCase by inject()
    private val saveCanvasViewportUseCase: SaveCanvasViewportUseCase by inject()
    private val saveCanvasEditUseCase: SaveCanvasEditUseCase by inject()
    private val upgradeCanvasLayoutsUseCase: UpgradeCanvasLayoutsUseCase by inject()
    private val observeRemoteCanvasUseCase: ObserveRemoteCanvasUseCase by inject()
    private val pruneCanvasOrphansUseCase: PruneCanvasOrphansUseCase by inject()

    /* ============================ CANVAS — reads ============================ */

    fun readCanvas(
        noteId: String,
        onLoading: () -> Unit,
        onSuccess: (CanvasDocument?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable =
        subscribeTo(scope, { readCanvasUseCase(noteId) }, onLoading, onSuccess, onError)

    fun readViewport(
        noteId: String,
        onLoading: () -> Unit,
        onSuccess: (Viewport?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable =
        subscribeTo(scope, { readCanvasViewportUseCase(noteId) }, onLoading, onSuccess, onError)

    /* ============================ CANVAS — writes ============================ */

    fun save(
        noteId: String,
        node: CanvasNode,
        onSuccess: (CanvasNode?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { saveCanvasNodeUseCase(noteId, node) }, {}, onSuccess, onError
    )

    fun saveAll(
        noteId: String,
        nodes: List<CanvasNode>,
        onSuccess: (List<CanvasNode>?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { saveCanvasNodesUseCase(noteId, nodes) }, {}, onSuccess, onError
    )

    fun move(
        nodeId: String,
        x: Float,
        y: Float,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { moveCanvasNodeUseCase(nodeId, x, y) }, {}, onSuccess, onError
    )

    fun resize(
        nodeId: String,
        width: Float,
        height: Float,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { resizeCanvasNodeUseCase(nodeId, width, height) }, {}, onSuccess, onError
    )

    fun rename(
        nodeId: String,
        name: String,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { renameCanvasNodeUseCase(nodeId, name) }, {}, onSuccess, onError
    )

    fun remove(
        nodeId: String,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { removeCanvasNodeUseCase(nodeId) }, {}, onSuccess, onError
    )

    fun removeAll(
        noteId: String,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { clearCanvasUseCase(noteId) }, {}, onSuccess, onError
    )

    fun saveViewport(
        noteId: String,
        viewport: Viewport,
        onSuccess: (Viewport?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { saveCanvasViewportUseCase(noteId, viewport) }, {}, onSuccess, onError
    )

    // 🔄 24-Sep-2026 — a user's layout edit, written in one go; onSuccess is where the note gets stamped to travel
    fun saveEdit(
        noteId: String,
        nodes: List<CanvasNode>,
        removedIds: List<String>,
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { saveCanvasEditUseCase(noteId, nodes, removedIds) }, {}, onSuccess, onError
    )

    // 📐 24-Sep-2026 — the one-time move of every stored canvas to the compact layout
    fun upgradeLayouts(
        unitScale: Float,
        maxPaperWidth: Float,
        onSuccess: (Int?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { upgradeCanvasLayoutsUseCase(unitScale, maxPaperWidth) }, {}, onSuccess, onError
    )

    // 🔄 24-Sep-2026 — widgets whose content was deleted elsewhere, dropped before a board is read
    fun pruneOrphans(
        onSuccess: (Boolean?) -> Unit,
        onError: (BridgeError) -> Unit
    ): Closeable = subscribeTo(
        writeScope, { pruneCanvasOrphansUseCase() }, {}, onSuccess, onError
    )

    // 🔄 24-Sep-2026 — a canvas another device saved, for the master editor that is open on this note
    fun observeRemoteCanvas(noteId: String, onChange: (List<CanvasNode>) -> Unit): Closeable =
        observeRemoteCanvasUseCase(noteId).watch(scope) { onChange(it) }

    fun dispose() = scope.cancel()
}
