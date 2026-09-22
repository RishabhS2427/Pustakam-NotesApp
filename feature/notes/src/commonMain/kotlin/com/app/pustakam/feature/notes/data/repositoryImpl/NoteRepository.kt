package com.app.pustakam.feature.notes.data.repositoryImpl

import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.Tag
import com.app.pustakam.core.model.models.response.DeleteDataModel
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteSummary
import com.app.pustakam.core.model.models.response.notes.NOTES_PAGE_SIZE
import com.app.pustakam.core.model.models.response.notes.Notes
import com.app.pustakam.core.model.models.response.notes.toSummary
import com.app.pustakam.core.data.base.BaseRepository
import com.app.pustakam.core.common.extensions.isNotnull
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.ErrorMessage
import com.app.pustakam.core.common.util.NetworkError
import kotlinx.coroutines.flow.first
import com.app.pustakam.core.common.util.UniqueIdGenerator
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.common.util.log_d
import com.app.pustakam.core.common.util.onError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.app.pustakam.feature.notes.domain.repository.INoteRepository
import com.app.pustakam.feature.notes.domain.repository.INoteSyncRepository
import com.app.pustakam.feature.notes.domain.repository.ISyncRepository
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.onSuccess
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import org.koin.core.component.inject

internal class NoteRepository : BaseRepository(), INoteRepository {
        private val syncRepository: INoteSyncRepository by inject()

    // 🔄 28-Aug-2026 — server sync. Lazy on purpose: SyncRepository injects THIS repository back,
    //   and only a lazy resolve on both sides keeps that cycle from biting during construction.
    private val remoteSync: ISyncRepository by inject()
        private val _notes= MutableStateFlow(Notes())
        private val _tags= MutableStateFlow<List<Tag>>(emptyList())

    override val notesState: StateFlow<Notes> = _notes.asStateFlow()
    override val tagState: StateFlow<List<Tag>> = _tags.asStateFlow()
    private val _noteSummaries = MutableStateFlow<List<NoteSummary>>(emptyList())
    override val noteSummariesState: StateFlow<List<NoteSummary>> = _noteSummaries.asStateFlow()
    /** create an blank note
     */
    private fun createNewEmptyNote(tagId  : String = ""): Note {
        val date = getCurrentTimestamp().toString()
        val id = UniqueIdGenerator.generateUniqueId()
        return Note(
            id = id, title = "", updatedAt = date, createdAt = date, categoryId = tagId,
            ownerId = session.userId,
            syncStatus = "PENDING",
        ).stampedWithNextVersion()
    }
    fun insertNotes(notes : Notes){
        _notes.update { current ->
            val list: ArrayList<Note> = if (notes.page > 1) {
                ArrayList(current.notes).apply {
                    val existingIds = map { it.id }.toSet()
                    addAll(notes.notes.filterNot { it.id in existingIds })
                }
            } else {
                arrayListOf<Note>().apply { addAll(notes.notes) }
            }
            current.copy(notes = list, page = notes.page)
        }
        log_d("NoteRepository insert Notes" , _notes.value.notes.count())
    }
    fun insertTag(tags : List<Tag>){
        _tags.update {
            val list : ArrayList<Tag> = arrayListOf()
            list += tags
            list
        }
        log_d("NoteRepository insert Tags" , _tags.value.count())
    }

    /**---------------- NOTES API SERVER CALL ------------*/
    /** 🔒 22-Sep-2026 — offline mode has to hold on EVERY route to the server, not just the one
     *  the sync engine takes. These wrappers have no live call site today; they are the remote
     *  entry points on IRemoteNoteRepository, and the day one is switched back on the guarantee
     *  must already be true here rather than remembered by whoever switches it on. */
    private suspend fun offlineModeOn(): Boolean = userPrefs.offlineModeFlow.first()

    /** get all notes from server api call */
    override suspend fun getNotesForUserApi(page: Int): Result<BaseResponse<Notes>, Error> {
        return apiClient.getNotes(session.userId).onSuccess {
            it.data?.let { it1 -> notesDao.insertNotes(it1)
            }
        }
    }
    /** update or insert note to server apis call */
    override suspend fun upsertNewNoteApi(note: Note): Result<BaseResponse<Note>, Error> {
        if (offlineModeOn()) {
            insertUpdateFromDb(note)
            return Result.Error(NetworkError.OFFLINE_MODE)
        }
        return  apiClient.addNewNote(session.userId, note).onSuccess {
            it.data?.let { it1 ->
                log_d("NoteRepository", "addNewNote: $it1")
                insertUpdateFromDb(it1)
            }
        }
    }
    /** update note apis call to server*/
    override suspend fun updateNoteApi(note: Note): Result<BaseResponse<Note>, Error> {
        if (offlineModeOn()){
            insertUpdateFromDb(note)
            return Result.Error(NetworkError.OFFLINE_MODE)
        }
        return apiClient.updateNote(session.userId, note).onSuccess {
            it.data?.let {
                    it1 ->
                log_d("NoteRepository", "addNewNote: $it1")
                insertUpdateFromDb(it1)
            }
        }
    }

    /** delete note apis call to server */
    override suspend fun deleteNoteApi(noteId: String): Result<BaseResponse<DeleteDataModel>, Error> {
        if (offlineModeOn())  {
            deleteNoteByIdFromDb(noteId)
           return Result.Error(NetworkError.OFFLINE_MODE)
        }
        return apiClient.deleteNote(session.userId, noteId).onSuccess {
            deleteNoteByIdFromDb(noteId)
        }
    }

    /**  get a note apis call from server */
    override suspend fun getNoteApi(noteId: String): Result<BaseResponse<Note>, Error> {
        return apiClient.getNote(session.userId, noteId)
    }


    /**-----------------------LOCAL DATABASE -------------*/

    /** insert or update a note data from local db
     * 🔧 15-Jul-2026 Phase 0.4: optional dirtyContentIds — when provided, only those content rows
     *   are rewritten (see NotesDao). null keeps the legacy full write. */
    override suspend fun insertUpdateFromDb(note: Note): Result<BaseResponse<Note>, Error> =
        insertUpdateFromDb(note, dirtyContentIds = null)

    fun insertUpdateFromDb(note: Note, dirtyContentIds: Set<String>?): Result<BaseResponse<Note>, Error> {
       return try {
            val newNote = notesDao.insertOrUpdateNoteFromDb(note.stampedWithNextVersion(), dirtyContentIds)
            return if(newNote.isNotnull()) {
                val response = BaseResponse(data = newNote , isSuccessful = true,
                    isFromDb = true)
                Result.Success(response)
            } else {
                Result.Error(error = NetworkError.NOT_FOUND)
            }
        } catch (e : Exception) {
           println(e.printStackTrace())
           Result.Error(error = ErrorMessage(e.stackTraceToString()))
        }
    }
    // 🔄 20-Aug-2026 sync: called after a pull commits, so the list screen shows what arrived
    // 🔄 29-Aug-2026 — one page of HEADROOM. The list is ordered by id, and ids are timestamp
    //   prefixed, so a note just made on the other device sorts LAST. Re-reading exactly as many
    //   rows as were already loaded therefore never included it: it synced and stayed invisible.
    override suspend fun refreshFromDb() {
        val loaded = _noteSummaries.value.size + NOTES_PAGE_SIZE
        _noteSummaries.value = notesDao.selectNoteSummariesPage(limit = loaded, page = 1)
        val cached = _notes.value.notes.size + NOTES_PAGE_SIZE
        _notes.value = notesDao.selectAllNotesFromDb(limit = cached, page = 1)
    }

    /** delete a note data from local db */
    override suspend fun deleteNoteByIdFromDb(id: String?): Result<BaseResponse<Boolean>, Error> {
        // 🔧 F3: missing `return` — the null-check was dead code, then id!! could NPE
        if (id.isNullOrEmpty()) return Result.Error(error = NetworkError.NOT_FOUND)
        val success =  notesDao.deleteNoteByIdFromDb(id)
        return  if(success){
            val response = BaseResponse(data = success, isSuccessful = true, isFromDb = true)
            Result.Success(response)
        }
        else  Result.Error(error = NetworkError.SERVER_ERROR)
    }

    /** get notes data from local db
     * 🔧 15-Jul-2026 Phase 0.1: limit param added (limit > 0 = paged). Also fixes a latent bug:
     *   `selectAllNotesFromDb(page)` passed page POSITIONALLY as the DAO's `limit` parameter. */
    override suspend fun getNotesFromDb( page: Int, limit: Int ): Result<BaseResponse<Notes>, Error> {
        val notes  = notesDao.selectAllNotesFromDb(limit = limit, page = page)
        val response = BaseResponse(data = notes ,
            isSuccessful = true,
            isFromDb = true)
        return  Result.Success(response)

    }
    /** get a note data from local db */
    override suspend fun getNoteByIdFromDb(id: String?): Result<BaseResponse<Note>, Error> {
        if(id.isNullOrEmpty()) return Result.Error(error = NetworkError.NOT_FOUND)
        val note = notesDao.selectNoteById(id)
        return if(note.isNotnull()) {
            val response = BaseResponse(data = note , isSuccessful = true,
                isFromDb = true)
            Result.Success(response)
        } else {
            Result.Error(error = NetworkError.NOT_FOUND)
        }
    }

    override suspend fun updateReadingProgressFromDb(
        contentId: String, progressPage: Int, totalPages: Int
    ): Result<BaseResponse<Boolean>, Error> {
        if (contentId.isEmpty() || totalPages <= 0) return Result.Error(error = NetworkError.NOT_FOUND)
        notesDao.updateReadingProgress(contentId, progressPage.coerceIn(0, totalPages - 1), totalPages)
        return Result.Success(BaseResponse(data = true, isSuccessful = true))
    }

    /** methods for deleting note content from db
     * */
    override suspend fun deleteNoteContentFromDb(id: String?): Result<BaseResponse<Boolean>, Error> {
        // 🔧 F3: missing `return` fixed (same dead-code pattern)
        if (id.isNullOrEmpty()) return Result.Error(error = NetworkError.NOT_FOUND)
        notesDao.deleteNoteContentById(id)
        return Result.Success(BaseResponse(data = true, isSuccessful = true))
    }

    override suspend fun getNoteContentByIdFromDb(id: String?): Result<BaseResponse<NoteContentModel>, Error> {
        if (id.isNullOrEmpty()) return Result.Error(error = NetworkError.NOT_FOUND)
        val content = notesDao.getNoteContentById(id)
        return  if (content.isNotnull()) Result.Success(BaseResponse(data = content, isSuccessful = true))
        else Result.Error(error = NetworkError.NOT_FOUND)
    }

    /** CRUD ON Tags/Categories
     *  🔧 F3 (piece 4): every DB mutation updates _tags immediately — single source of truth */
    override suspend fun createTagOnDB(tag: Tag): Result<BaseResponse<Tag>, Error> {
        val created = notesDao.createTagOnDB(tag)                 // 🔧 P6: no param shadowing
            ?: return Result.Error(error = NetworkError.SERVER_ERROR)
        _tags.update { it + created }
        return Result.Success(BaseResponse(data = created, isSuccessful = true, isFromDb = true))
    }

    override suspend fun updateTagOnDB(tag: Tag): Result<BaseResponse<Tag>, Error> {
        val updated = notesDao.updateTagOnDB(tag)
            ?: return Result.Error(error = NetworkError.NOT_FOUND)
        _tags.update { list ->                                    // 🔧 P1: observers now see renames/recolors
            list.map { if (it.id == updated.id) updated else it }
        }
        return Result.Success(BaseResponse(data = updated, isSuccessful = true, isFromDb = true))
    }

    override suspend fun deleteTagOnDB(tag: String?): Result<BaseResponse<Boolean>, Error> {
        if (tag.isNullOrEmpty()) return Result.Error(error = NetworkError.NOT_FOUND)
        val deleted = notesDao.deleteTag(tag)
        if (!deleted) return Result.Error(error = NetworkError.SERVER_ERROR)
        _tags.update { list -> list.filterNot { it.id == tag } } // 🔧 P2: observers see deletion
        return Result.Success(BaseResponse(data = true, isSuccessful = true, isFromDb = true))
    }

    override suspend fun getTagsFromDB(): Result<BaseResponse<List<Tag>>, Error> {
        val tags = notesDao.getTagsFromDB()
        _tags.value = tags   // 🔧 refresh observers BEFORE the empty-check (empty list is valid state)
        if (tags.isEmpty())  return Result.Error(error = NetworkError.NOT_FOUND)
        return Result.Success(BaseResponse(data = tags, isSuccessful = true, isFromDb = true))
    }

    /**---------CRUD LOGIC METHODS --------------*/
    /** methods for decision logic
     * - call local db methods or
     * - call api for server
     * - insert or update note
    // step 1 * check with local db
     * data is present call update server apis
     * else call create server apis
    // step 2 insert or update into local db
    // step 3 call api to upsert the data or sync with server
    // step 4 again update the local db with sync data.
     */
    // 🔧 15-Jul-2026 Phase 0.4: dirtyContentIds flows through to the DAO (null = full write)
    override suspend fun insertOrUpdateNote(note : Note, dirtyContentIds: Set<String>?) : Result<BaseResponse<Note>, Error> {
        return insertUpdateFromDb(note, dirtyContentIds).onSuccess { response ->
            log_d("Insert Update","added ")
            // 🔧 20-Aug-2026 sync: the SAVED note (version-stamped) is what the flows must carry
            val saved = response.data ?: note
            syncRepository.publishContents(note.id, note.contents)
            _notes.update { current->
                val newList = ArrayList(current.notes)                    // 1. copy FIRST
                val index = newList.indexOfFirst { it.id == note.id }     // 2. single O(n) scan
                if (index != -1) newList[index] = saved else newList.add(saved)
                current.copy(notes = newList)
            }
            // 🔧 15-Jul-2026 Summary query: keep the list-screen summaries in sync without a re-query
            _noteSummaries.update { current ->
                val summary = saved.toSummary()
                val index = current.indexOfFirst { s -> s.id == note.id }
                if (index != -1) current.toMutableList().apply { this[index] = summary }
                else current + summary
            }
            // 🔄 28-Aug-2026 — SYNC ON SAVE. Debounced inside the engine, so typing is one push.
            //   Offline this is a no-op that costs nothing: the note is already safe in SQLite and
            //   the connectivity trigger flushes it the moment there is a line again.
            remoteSync.requestSyncSoon()
//              if(existingNote != null ) {
//                  updateNoteApi(note)
//              }else upsertNewNoteApi(note)
        }
    }
    /** method for decision logic
     * - delete from local db
     * - call delete api from server
     * */
    override suspend fun deleteNote(id : String): Result<BaseResponse<Boolean>, Error> {
        return deleteNoteByIdFromDb(id).onSuccess {
            _notes.update { current ->
                val newList = ArrayList(current.notes.filterNot { n -> n.id == id })
                if (newList.size != current.notes.size) current.copy(notes = newList) else current
            }
            // 🔧 15-Jul-2026 Summary query: mirror the deletion into the summaries flow
            _noteSummaries.update { current -> current.filterNot { s -> s.id == id } }
            // 🔄 28-Aug-2026 — a delete has to travel too; the tombstone is what carries it
            remoteSync.requestSyncSoon()
        }
    }
    /** method for decision logic (A note)
     * - read from local db
     * - call read api from server
     * */
    override suspend fun getANote(id : String?): Result<BaseResponse<Note>, Error> {

        if (id.isNullOrEmpty()){
            return Result.Success(
                BaseResponse(data = createNewEmptyNote(),
                isFromDb = false, isSuccessful = false )
            )
        }

        return getNoteByIdFromDb(id).onSuccess {
//           getNoteApi(id)
        }
    }
    /** method for decision logic (\notes)
     * - read from local db
     * - call read api from server
     * */
    override suspend fun getNoteSummaries(page: Int, limit: Int): Result<BaseResponse<List<NoteSummary>>, Error> {
        val summaries = notesDao.selectNoteSummariesPage(limit = limit, page = page)
        _noteSummaries.update { current ->
            if (page > 1) {
                val existingIds = current.map { it.id }.toSet()
                current + summaries.filterNot { it.id in existingIds }
            } else summaries
        }
        return Result.Success(BaseResponse(data = summaries, isSuccessful = true, isFromDb = true))
    }

    override suspend fun searchNotes(query: String): Result<BaseResponse<List<NoteSummary>>, Error> {
        val results = notesDao.searchNotes(query)
        return Result.Success(BaseResponse(data = results, isSuccessful = true, isFromDb = true))
    }

    override suspend fun getAllNotes(page: Int, limit: Int): Result<BaseResponse<Notes>, Error> {
        return getNotesFromDb(page, limit).onSuccess { notes->
            if(notes.data != null && notes.data!!.notes.count() > 0){
              insertNotes(notes = notes.data!!)
            }
        }.onError {
        }
    }

}