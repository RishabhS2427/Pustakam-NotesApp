package com.app.pustakam.feature.notes.domain.repository

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.share.NoteAccessGate
import com.app.pustakam.core.model.models.share.NoteShare
import com.app.pustakam.core.model.models.share.NoteShareInfo
import com.app.pustakam.core.model.models.share.ShareMemberChange
import com.app.pustakam.core.model.models.share.ShareMemberRequest
import com.app.pustakam.core.model.models.share.StopSharingResult

interface IShareRepository {

    suspend fun share(
        noteId: String,
        members: List<ShareMemberRequest>,
        conversationId: String?,
        role: String?
    ): Result<BaseResponse<NoteShare>, Error>

    suspend fun shares(): Result<BaseResponse<List<NoteShare>>, Error>

    suspend fun changeMembers(shareId: String, changes: List<ShareMemberChange>): Result<BaseResponse<NoteShare>, Error>

    suspend fun stop(shareId: String): Result<BaseResponse<StopSharingResult>, Error>

    fun shareOf(noteId: String): NoteShareInfo?

    fun sharedNoteIds(): Set<String>

    fun accessOf(noteId: String?): NoteAccessGate

    suspend fun ensureLocal(noteId: String): Boolean
}
