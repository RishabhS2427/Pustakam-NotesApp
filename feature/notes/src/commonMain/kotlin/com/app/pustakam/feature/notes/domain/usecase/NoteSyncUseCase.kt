package com.app.pustakam.feature.notes.domain.usecase

import com.app.pustakam.core.data.usecases.BaseUseCase
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.richtext.master.model.CanvasNode
import com.app.pustakam.feature.notes.domain.repository.INoteSyncRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.inject

abstract class NoteSyncBaseUseCase : BaseUseCase() {
    protected val syncRepository: INoteSyncRepository by inject()
}

class ObserveNoteContentsUseCase : NoteSyncBaseUseCase() {
    operator fun invoke(noteId: String): Flow<List<NoteContentModel>> =
        syncRepository.observeContents(noteId)
}

class ObserveCanvasNodesUseCase : NoteSyncBaseUseCase() {
    operator fun invoke(noteId: String): Flow<List<CanvasNode>> =
        syncRepository.observeCanvasNodes(noteId)
}

class ObserveRemoteCanvasUseCase : NoteSyncBaseUseCase() {
    operator fun invoke(noteId: String): Flow<List<CanvasNode>> =
        syncRepository.observeRemoteCanvas(noteId)
}
