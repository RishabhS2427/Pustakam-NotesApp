package com.app.pustakam.feature.notes.di

import com.app.pustakam.feature.notes.data.repositoryImpl.CanvasRepository
import com.app.pustakam.feature.notes.data.repositoryImpl.NoteSyncRepository
import com.app.pustakam.feature.notes.data.repositoryImpl.SyncRepository
import com.app.pustakam.feature.notes.data.sync.MediaSyncer
import com.app.pustakam.feature.notes.data.repositoryImpl.NoteContentRepository
import com.app.pustakam.feature.notes.data.repositoryImpl.NoteRepository
import com.app.pustakam.feature.notes.domain.repository.ICanvasRepository
import com.app.pustakam.feature.notes.domain.repository.INoteSyncRepository
import com.app.pustakam.feature.notes.domain.repository.ISyncRepository
import com.app.pustakam.feature.notes.domain.repository.ILocalNotesRepository
import com.app.pustakam.feature.notes.domain.repository.INoteContentRepository
import com.app.pustakam.feature.notes.domain.repository.INoteRepository
import com.app.pustakam.feature.notes.domain.repository.IRemoteNoteRepository
import com.app.pustakam.feature.notes.domain.usecase.ClearCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.ClearSelectedNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.MoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.PruneCanvasOrphansUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveCanvasNodesUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveNoteContentsUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadCanvasViewportUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadVisibleCanvasUseCase
import com.app.pustakam.feature.notes.domain.usecase.RemoveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.RenameCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.ResizeCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasNodeUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasNodesUseCase
import com.app.pustakam.feature.notes.domain.usecase.SaveCanvasViewportUseCase
import com.app.pustakam.feature.notes.domain.usecase.CreateORUpdateNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.CreateTagUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.DeleteTagUseCase
import com.app.pustakam.feature.notes.domain.usecase.GetNoteSummariesUseCase
import com.app.pustakam.feature.notes.domain.usecase.GetNotesUseCase
import com.app.pustakam.feature.notes.domain.usecase.GetSelectedMediaIndexUseCase
import com.app.pustakam.feature.notes.domain.usecase.GetTagCase
import com.app.pustakam.feature.notes.domain.usecase.ReadNoteUseCase
import com.app.pustakam.feature.notes.domain.usecase.SearchNotesUseCase
import com.app.pustakam.feature.notes.domain.usecase.SetSelectedNoteContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.ReadContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpdateReadingProgressUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpdateSelectedMediaContentUseCase
import com.app.pustakam.feature.notes.domain.usecase.UpdateTagUseCase
import com.app.pustakam.feature.notes.domain.usecase.NotifyConnectivityUseCase
import com.app.pustakam.feature.notes.domain.usecase.ObserveSyncStateUseCase
import com.app.pustakam.feature.notes.domain.usecase.RequestSyncUseCase
import com.app.pustakam.feature.notes.domain.usecase.SetSyncForegroundUseCase
import com.app.pustakam.feature.notes.domain.usecase.StartSyncUseCase
import com.app.pustakam.feature.notes.domain.usecase.SyncNowUseCase
import com.app.pustakam.core.media.download.MediaLandingHandler
import com.app.pustakam.core.media.upload.MediaUploadRetry
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

// 🔧 30-Jul-2026 02:10 — lifted VERBATIM out of :shared/koin/Koin.kt (was `repositoriesModules` + the notes half of `useCases`)
fun notesModule(): Module = module {
    single<INoteRepository> { NoteRepository() }
    single<ILocalNotesRepository> { get<INoteRepository>() }
    single<IRemoteNoteRepository> { get<INoteRepository>() }

    single<INoteContentRepository> { NoteContentRepository() }
    single<ICanvasRepository> { CanvasRepository() }
    single<INoteSyncRepository> { NoteSyncRepository() }
    // 🔄 20-Aug-2026 sync: single — it owns the run lock and the backoff counter, so a second
    //   instance would let two cycles push the same notes at once.
    // 📥 21-Sep-2026 — the SAME instance lands finished downloads; binds, because ISyncRepository is not a MediaLandingHandler
    single<ISyncRepository> { SyncRepository() } binds arrayOf(MediaLandingHandler::class)
    // ⬆️ 21-Sep-2026 — "Retry" on a failed upload is a sync nudge: uploads only ever run inside sync
    single<MediaUploadRetry> {
        val sync = get<ISyncRepository>()
        MediaUploadRetry { sync.requestSync() }
    }
    // 🖼️ stateless — it only reads and writes files and calls the API
    single<MediaSyncer> { MediaSyncer() }
    factory<CreateORUpdateNoteUseCase> { CreateORUpdateNoteUseCase() }
    factory<DeleteNoteUseCase> { DeleteNoteUseCase() }
    factory<ReadNoteUseCase> { ReadNoteUseCase() }
    factory<GetNotesUseCase> { GetNotesUseCase() }
    factory<GetNoteSummariesUseCase> { GetNoteSummariesUseCase() }
    factory<SearchNotesUseCase> { SearchNotesUseCase() }
    factory<DeleteNoteContentUseCase> { DeleteNoteContentUseCase() }
    factory<UpdateReadingProgressUseCase> { UpdateReadingProgressUseCase() }
    factory<ReadContentUseCase> { ReadContentUseCase() }
    factory<ReadCanvasUseCase> { ReadCanvasUseCase() }
    factory<ObserveNoteContentsUseCase> { ObserveNoteContentsUseCase() }
    factory<ObserveCanvasNodesUseCase> { ObserveCanvasNodesUseCase() }
    factory<ReadVisibleCanvasUseCase> { ReadVisibleCanvasUseCase() }
    factory<ReadCanvasViewportUseCase> { ReadCanvasViewportUseCase() }
    factory<SaveCanvasNodeUseCase> { SaveCanvasNodeUseCase() }
    factory<SaveCanvasNodesUseCase> { SaveCanvasNodesUseCase() }
    factory<MoveCanvasNodeUseCase> { MoveCanvasNodeUseCase() }
    factory<ResizeCanvasNodeUseCase> { ResizeCanvasNodeUseCase() }
    factory<RenameCanvasNodeUseCase> { RenameCanvasNodeUseCase() }
    factory<RemoveCanvasNodeUseCase> { RemoveCanvasNodeUseCase() }
    factory<ClearCanvasUseCase> { ClearCanvasUseCase() }
    factory<SaveCanvasViewportUseCase> { SaveCanvasViewportUseCase() }
    factory<PruneCanvasOrphansUseCase> { PruneCanvasOrphansUseCase() }
    factory<GetTagCase> { GetTagCase() }
    factory<CreateTagUseCase> { CreateTagUseCase() }
    factory<UpdateTagUseCase> { UpdateTagUseCase() }
    factory<DeleteTagUseCase> { DeleteTagUseCase() }
    factory<SetSelectedNoteContentUseCase> { SetSelectedNoteContentUseCase() }
    factory<UpdateSelectedMediaContentUseCase> { UpdateSelectedMediaContentUseCase() }
    factory<GetSelectedMediaIndexUseCase> { GetSelectedMediaIndexUseCase() }
    factory<ClearSelectedNoteContentUseCase> { ClearSelectedNoteContentUseCase() }
    factory<StartSyncUseCase> { StartSyncUseCase() }
    factory<SyncNowUseCase> { SyncNowUseCase() }
    factory<RequestSyncUseCase> { RequestSyncUseCase() }
    factory<NotifyConnectivityUseCase> { NotifyConnectivityUseCase() }
    factory<SetSyncForegroundUseCase> { SetSyncForegroundUseCase() }
    factory<ObserveSyncStateUseCase> { ObserveSyncStateUseCase() }
}
