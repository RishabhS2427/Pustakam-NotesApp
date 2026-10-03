package com.app.pustakam.feature.notes.data.repositoryImpl

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.database.localdb.database.NotesDao
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.share.CreateShareRequest
import com.app.pustakam.core.database.localdb.preferences.BasePreferences
import com.app.pustakam.core.model.models.share.NoteAccessGate
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.NoteShareInfo
import com.app.pustakam.core.model.models.share.ShareMemberChange
import com.app.pustakam.core.model.models.share.ShareMemberRequest
import com.app.pustakam.core.model.models.share.StopSharingResult
import com.app.pustakam.core.model.models.share.UpdateShareMembersRequest
import com.app.pustakam.core.network.ApiCallClient
import com.app.pustakam.feature.notes.domain.repository.IShareRepository
import com.app.pustakam.feature.notes.domain.repository.ISyncRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class ShareRepository : IShareRepository, KoinComponent {

    private val api by inject<ApiCallClient>()
    private val sync by inject<ISyncRepository>()
    private val notesDao by inject<NotesDao>()
    private val prefs by inject<BasePreferences>()

    override suspend fun share(
        noteId: String,
        members: List<ShareMemberRequest>,
        conversationId: String?,
        role: String?
    ): Result<BaseResponse<NoteShare>, Error> {
        sync.syncNow()
        return api.createShare(CreateShareRequest(noteId, members, conversationId, role)).also(::pullAfter)
    }

    override suspend fun shares(): Result<BaseResponse<List<NoteShare>>, Error> = api.listShares()

    override suspend fun changeMembers(shareId: String, changes: List<ShareMemberChange>): Result<BaseResponse<NoteShare>, Error> =
        api.updateShareMembers(shareId, UpdateShareMembersRequest(changes)).also(::pullAfter)

    override suspend fun stop(shareId: String): Result<BaseResponse<StopSharingResult>, Error> =
        api.stopSharing(shareId).also(::pullAfter)

    override fun shareOf(noteId: String): NoteShareInfo? = notesDao.shareOf(noteId)

    override fun sharedNoteIds(): Set<String> = notesDao.sharedNoteIds()

    override fun accessOf(noteId: String?): NoteAccessGate =
        NoteAccessGate(noteId?.takeIf { it.isNotEmpty() }?.let(notesDao::shareOf), prefs.currentUserId())

    override suspend fun ensureLocal(noteId: String): Boolean {
        if (notesDao.shareOf(noteId) != null) return true
        sync.syncNow()
        return notesDao.shareOf(noteId) != null
    }

    private fun pullAfter(result: Result<*, Error>) {
        if (result is Result.Success) sync.requestSync()
    }
}
