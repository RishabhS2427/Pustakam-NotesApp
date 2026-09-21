package com.app.pustakam.core.database.localdb.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.app.pustakam.core.model.models.Tag
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.model.models.response.notes.Notes
import com.app.pustakam.core.database.NoteContent
import com.app.pustakam.core.database.NotesDatabase
import com.app.pustakam.core.database.localdb.preferences.BasePreferences
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.common.util.getCurrentTimestamp
import com.app.pustakam.core.common.util.isDoc
import com.app.pustakam.core.common.util.isMedia
import com.app.pustakam.core.common.util.log_d
import org.koin.core.component.KoinComponent
import com.app.pustakam.core.model.models.RichTextMetadata
import com.app.pustakam.core.model.models.response.notes.NoteContentModel.*
import com.app.pustakam.core.model.models.response.notes.NoteSummary
import org.koin.core.component.get

// 🔄 20-Aug-2026 sync: the schema default is 'PENDING', so "dirty" is "not SYNCED", not "== PENDING"
const val SYNC_STATUS_SYNCED = "SYNCED"

class NotesDao : KoinComponent {

    private val database =  get<NotesDatabase>()
    private val driver = get<SqlDriver>()

    // 🔒 notes are read and written for the logged-in user only
    private val prefs = get<BasePreferences>()
    private val userId: String get() = prefs.currentUserId()

    private val queries = database.notesDatabaseQueries

    fun createTagOnDB(tag : Tag) : Tag?   {
       println("Create Tab On DB called with tag: $tag") // Debug log
        database.transaction {
            queries.createTag(id= tag.id, label = tag.label, color= tag.color)
        }
        return getTag(tag.id)
    }
      fun getTag(id : String) : Tag? =  queries.getTag(id).executeAsOneOrNull()?.let {
            Tag(id = it.id, label = it.label, color= it.color)
        }
    fun updateTagOnDB(tag: Tag) : Tag? {
      database.transaction {
          queries.updateTag(color = tag.color, label = tag.label, id = tag.id)
      }
        return getTag(tag.id)
    }
    fun deleteTag(tagId  : String) : Boolean {
        queries.deleteTag(tagId)
        val tag  = getTag(tagId)
        return tag == null
    }

    fun getTagsFromDB() : List<Tag> = queries.getTags().executeAsList().map{
          Tag(id = it.id, label = it.label, color= it.color)
    }
   fun selectNoteSummariesPage(limit: Int, page: Int): List<com.app.pustakam.core.model.models.response.notes.NoteSummary> {
       val offset = ((page - 1) * limit).coerceAtLeast(0)
       return queries.selectNoteSummariesPage(userId, limit.toLong(), offset.toLong()).executeAsList().map { row ->
           com.app.pustakam.core.model.models.response.notes.NoteSummary(
               id = row.id,
               title = row.title,
               categoryId = row.categoryId,
               createdAt = row.createdAt,
               updatedAt = row.updatedAt,
               snippet = row.snippet,
               contentCount = (row.contentCount ?: 0L).toInt(),
               imageCount = (row.imageCount ?: 0L).toInt(),
               videoCount = (row.videoCount ?: 0L).toInt(),
               audioCount = (row.audioCount ?: 0L).toInt(),
               docCount = (row.docCount ?: 0L).toInt(),
               thumbnailPath = com.app.pustakam.core.common.util.resolveLocalFilePath(row.thumbnailPath),
           )
       }
   }
   private var ftsAvailable: Boolean? = null

   private fun ensureFtsIndex(): Boolean {
       ftsAvailable?.let { return it }
       val available = try {
           val existedBefore = ftsTableExists()
           driver.execute(null,
               "CREATE VIRTUAL TABLE IF NOT EXISTS NoteContentFts USING fts5(text, content='NoteContent', content_rowid='rowid')", 0).value
           driver.execute(null,
               "CREATE TRIGGER IF NOT EXISTS note_content_fts_insert AFTER INSERT ON NoteContent BEGIN " +
                       "INSERT INTO NoteContentFts(rowid, text) VALUES (new.rowid, new.text); END", 0).value
           driver.execute(null,
               "CREATE TRIGGER IF NOT EXISTS note_content_fts_delete AFTER DELETE ON NoteContent BEGIN " +
                       "INSERT INTO NoteContentFts(NoteContentFts, rowid, text) VALUES ('delete', old.rowid, old.text); END", 0).value
           driver.execute(null,
               "CREATE TRIGGER IF NOT EXISTS note_content_fts_update AFTER UPDATE ON NoteContent BEGIN " +
                       "INSERT INTO NoteContentFts(NoteContentFts, rowid, text) VALUES ('delete', old.rowid, old.text); " +
                       "INSERT INTO NoteContentFts(rowid, text) VALUES (new.rowid, new.text); END", 0).value
           if (!existedBefore) {
               driver.execute(null, "INSERT INTO NoteContentFts(NoteContentFts) VALUES('rebuild')", 0).value
           }
           true
       } catch (t: Throwable) {
           log_d("NotesDao", "FTS5 unavailable on this device, using LIKE search: ${t.message}")
           false
       }
       ftsAvailable = available
       return available
   }

   private fun ftsTableExists(): Boolean = driver.executeQuery(null,
       "SELECT name FROM sqlite_master WHERE type='table' AND name='NoteContentFts'",
       { cursor -> QueryResult.Value(cursor.next().value) }, 0).value
   private fun searchContentViaFts(match: String): List<com.app.pustakam.core.model.models.response.notes.NoteSummary> {
       val results = mutableListOf<com.app.pustakam.core.model.models.response.notes.NoteSummary>()
       driver.executeQuery(null,
           "SELECT n.id, n.categoryId, n.title, n.createdAt, n.updatedAt, SUBSTR(c.text, 1, 200) " +
                   "FROM NoteContentFts " +
                   "JOIN NoteContent c ON c.rowid = NoteContentFts.rowid " +
                   "JOIN Notes n ON n.id = c.noteId " +
                   "WHERE NoteContentFts MATCH ? AND n.deleted = 0 " +
                   "GROUP BY n.id ORDER BY n.updatedAt DESC LIMIT 50",
           { cursor ->
               while (cursor.next().value) {
                   results.add(
                       com.app.pustakam.core.model.models.response.notes.NoteSummary(
                           id = cursor.getString(0)!!,
                           categoryId = cursor.getString(1),
                           title = cursor.getString(2),
                           createdAt = cursor.getString(3),
                           updatedAt = cursor.getString(4),
                           snippet = cursor.getString(5),
                       )
                   )
               }
               QueryResult.Unit
           }, 1) { bindString(0, match) }.value
       return results
   }

   fun searchNotes(rawQuery: String): List<NoteSummary> {
       val trimmed = rawQuery.trim()
       if (trimmed.isEmpty()) return emptyList()
       val merged = LinkedHashMap<String, NoteSummary>()
       val contentMatches = try {
           if (ensureFtsIndex()) {
               searchContentViaFts("\"" + trimmed.replace("\"", "") + "\"*")
           } else {
               queries.searchContentTextLike(trimmed, userId).executeAsList().map { row ->
                   NoteSummary(
                       id = row.id, title = row.title, categoryId = row.categoryId,
                       createdAt = row.createdAt, updatedAt = row.updatedAt, snippet = row.snippet,
                   )
               }
           }
       } catch (t: Throwable) {
           log_d("NotesDao", "content search failed: ${t.message}")
           emptyList()
       }
       contentMatches.forEach { merged[it.id] = it }
       queries.searchTitles(trimmed, userId).executeAsList().forEach { row ->
           if (!merged.containsKey(row.id)) merged[row.id] = NoteSummary(
               id = row.id, title = row.title, categoryId = row.categoryId,
               createdAt = row.createdAt, updatedAt = row.updatedAt,
           )
       }
       return merged.values.sortedByDescending { it.updatedAt ?: "" }
   }
   fun selectAllNotesFromDb(limit : Int = 0, page : Int = 0): Notes {
      if (limit > 0 && page > 0) {
          val pagedOffset = ((page - 1) * limit).coerceAtLeast(0)
          val ids = queries.selectNoteIdsPage(limit.toLong(), pagedOffset.toLong()).executeAsList()
          val pagedNotes = arrayListOf<Note>()
          ids.forEach { id -> selectNoteById(id)?.let { pagedNotes.add(it) } }
          return Notes(notes = pagedNotes, count = pagedNotes.size, page = page)
      }
      val notesWithContent = arrayListOf<Note>()
      val results  =  queries.selectWithAllContent(userId).executeAsList()
      val grouped = results.groupBy { it.noteId }
      grouped.forEach { (_, rows) ->
          val note = rows.first()
          notesWithContent.add(
              Note(
                  id = note.noteId,
                  categoryId = note.categoryId,
                  title = note.title,
                  createdAt = note.noteCreatedAt,
                  updatedAt = note.noteUpdatedAt,
                  ownerId = note.ownerId ?: userId,
                  version = note.version,
                  syncStatus = note.syncStatus,
                  deleted = note.deleted == 1L,
                  deletedAt = note.deletedAt,
                  serverUpdatedAt = note.serverUpdatedAt,
                  contents = rows.mapNotNull { row ->
                      if (row.contentId != null&& !row.type.isNullOrEmpty()) {
                          val type = ContentType.valueOf(row.type)
                          when (type) {
                              ContentType.TEXT ->
                                  NoteContentModel.TextContent(
                                      id = row.contentId,
                                      noteId = row.noteId,
                                      text =  row.text!!,
                                      position = row.position!!,
                                      createdAt = row.contentCreatedAt,
                                      updatedAt = row.contentUpdatedAt,
                                      metadata =  row.metaData
                                  )
                              ContentType.IMAGE, ContentType.DOCX,  ContentType.VIDEO, ContentType.AUDIO, ContentType.PDF, ContentType.GIF,
                              ContentType.TXT, ContentType.MD, ContentType.EPUB, ContentType.OTHER  ->
                                  NoteContentModel.MediaContent(title = row.contentTitle?:"${row.type}-${row.position}",
                                  id = row.contentId,
                                  noteId = row.noteId,
                                  url = row.url!!,
                                  position = row.position!!,
                                  createdAt = row.contentCreatedAt,
                                  updatedAt = row.contentUpdatedAt,
                                  localPath = row.localPath, duration = row.duration?:0,
                                  type =  type,
                                  mimeType = row.mimeType ?: "",
                                  sizeBytes = row.sizeBytes ?: 0,
                                  width = row.width?.toInt() ?: 0,
                                  height = row.height?.toInt() ?: 0,
                                  thumbnailPath = row.thumbnailPath,
                                  totalPages = (row.totalPages ?: 0L).toInt(),
                                  progressPage = (row.progressPage ?: 0L).toInt(),
                                  assetId = row.assetId,
                                  checksum = row.checksum,
                              )

                              ContentType.LINK -> NoteContentModel.Link(
                                  url = row.url!!,
                                  id = row.contentId,
                                  noteId = row.noteId,
                                  position = row.position!!,
                                  createdAt = row.contentCreatedAt,
                                  updatedAt = row.contentUpdatedAt,
                              )

                              ContentType.LOCATION -> NoteContentModel.Location(
                                  latitude = row.lat!!,
                                  longitude = row.long!!,
                                  address = row.address,
                                  position = row.position!!,
                                  id = row.contentId,
                                  noteId = row.noteId,
                                  createdAt = row.contentCreatedAt,
                                  updatedAt = row.contentUpdatedAt,
                              )

                              else -> null
                          }
                      } else null
                  }
              )
          )
      }

        return Notes(
            notes = notesWithContent,
            count = notesWithContent.size, page = 0
        )
    }

    fun insertNotes(notes: Notes) {
        database.transaction {
            notes.notes.forEach { note ->
                insertOrUpdateNoteFromDb(note)
            }
        }
        log_d("Note inserted count ", notes.notes.count())
   }
  private  fun insertOrUpdateNotesContent (noteContent: NoteContentModel) {
        var url = ""
        var text = ""
        var address: String? = null
        var duration : Long? = null
        var localPath: String? = null
        var long: Double? = null
        var lat: Double? = null
        var metadata : RichTextMetadata? = null
        var title: String? = null
        var mimeType: String? = null
        var sizeBytes: Long? = null
        var width: Long? = null
        var height: Long? = null
        var thumbnailPath: String? = null
        var totalPages: Long = 0
        var progressPage: Long = 0
        var assetId: String? = null
        var checksum: String? = null
        when (noteContent) {
            is NoteContentModel.TextContent -> {
                text = noteContent.text
                metadata = noteContent.metadata
            }
            is NoteContentModel.MediaContent -> {
                url = noteContent.url
                localPath = noteContent.localPath
                duration = noteContent.duration
                title = noteContent.title
                mimeType = noteContent.mimeType
                sizeBytes = noteContent.sizeBytes
                width = noteContent.width.toLong()
                height = noteContent.height.toLong()
                thumbnailPath = noteContent.thumbnailPath
                totalPages = noteContent.totalPages.toLong()
                progressPage = noteContent.progressPage.toLong()
                assetId = noteContent.assetId
                checksum = noteContent.checksum
            }
            is NoteContentModel.Location -> {
                address = noteContent.address
                lat = noteContent.latitude
                long = noteContent.longitude
            }
            is NoteContentModel.Link -> {
                url = noteContent.url
            }
        }
      queries.insertNoteContentById(
            id = noteContent.id,
            noteId = noteContent.noteId,
            createdAt = noteContent.createdAt,
            updatedAt = noteContent.updatedAt,
            position = noteContent.position,
            type = noteContent.type.name,
            text = text,
            duration = duration,
            url = url,
            localPath = localPath,
            long = long,
            lat = lat,
            address = address,
            title = title,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            thumbnailPath = thumbnailPath,
            totalPages = totalPages,
            progressPage = progressPage,
            metaData =  metadata,
            // 🖼️ 20-Aug-2026 sync: same reason — INSERT OR REPLACE would drop the server asset id
            assetId = assetId,
            checksum = checksum,
        )
    }

    // 🖼️ 21-Sep-2026 — one row, never the whole note: an upload can outlast the edits made while it ran
    fun stampMediaAsset(contentId: String, assetId: String, url: String, checksum: String?, mimeType: String, sizeBytes: Long) {
        queries.stampMediaAsset(
            assetId = assetId,
            url = url,
            checksum = checksum,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            id = contentId,
        )
    }

    // 📥 21-Sep-2026 — one row: a finished download's path, only where the row has none yet
    fun stampMediaLocalPath(contentId: String, localPath: String) {
        queries.stampMediaLocalPath(localPath = localPath, id = contentId)
    }

    fun updateReadingProgress(contentId: String, progressPage: Int, totalPages: Int) {
        queries.updateReadingProgress(
            progressPage = progressPage.toLong(),
            totalPages = totalPages.toLong(),
            updatedAt = "${getCurrentTimestamp()}",
            id = contentId
        )
    }
    fun  deleteNoteContentById(id : String) = queries.deleteNoteContentById(id)

    fun getNoteContentById(id: String): NoteContentModel? =
        queries.selectNoteContentById(id).executeAsOneOrNull()?.toNoteContentModel()
    private fun NoteContent.toNoteContentModel(): NoteContentModel? {
        if (type.isEmpty()) return null
        return when (val contentType = ContentType.valueOf(type)) {
            ContentType.TEXT -> TextContent(
                id = id, noteId = noteId, text = text ?: "", position = position ?: 0.0,
                createdAt = createdAt, updatedAt = updatedAt, metadata = metaData,
            )

            ContentType.IMAGE, ContentType.DOCX, ContentType.VIDEO, ContentType.AUDIO,
            ContentType.PDF, ContentType.GIF, ContentType.TXT, ContentType.MD,
            ContentType.EPUB, ContentType.OTHER -> MediaContent(
                title = title ?: "$type-$position",
                id = id, noteId = noteId, url = url ?: "", position = position ?: 0.0,
                createdAt = createdAt, updatedAt = updatedAt,
                localPath = localPath, duration = duration ?: 0, type = contentType,
                mimeType = mimeType ?: "", sizeBytes = sizeBytes ?: 0,
                width = width?.toInt() ?: 0, height = height?.toInt() ?: 0,
                thumbnailPath = thumbnailPath,
                totalPages = totalPages.toInt(), progressPage = progressPage.toInt(),
                assetId = assetId, checksum = checksum,
            )

            ContentType.LINK -> Link(
                url = url ?: "", id = id, noteId = noteId, position = position ?: 0.0,
                createdAt = createdAt, updatedAt = updatedAt,
            )

            ContentType.LOCATION -> Location(
                latitude = lat ?: 0.0, longitude = long ?: 0.0, address = address,
                position = position ?: 0.0, id = id, noteId = noteId,
                createdAt = createdAt, updatedAt = updatedAt,
            )

            ContentType.DRAWING -> TODO()
            ContentType.FORMULA -> TODO()
            ContentType.TABLE -> TODO()
        }
    }
    // 🔄 20-Aug-2026 sync: a tombstone, not a DELETE — a hard delete can never reach another device.
    //   The contents go immediately (mirrors the server's soft delete); the note row survives until
    //   the server has acknowledged the deletion, then purgeAckedTombstones drops it.
    suspend fun deleteNoteByIdFromDb(id: String) : Boolean {
        val now = "${getCurrentTimestamp()}"
        database.transaction {
            queries.deleteNoteContentsForNote(id)
            // 🎨 canvas nodes are derived layout, regenerated from the note on first open
            queries.deleteCanvasNodesForNote(id)
            queries.softDeleteNoteById(deletedAt = now, updatedAt = now, id = id)
        }
        // 🔧 keeps the old contract: deleting a note that was never saved still counts as success
        val row = selectNoteByIdIncludingDeleted(id)
        return row == null || row.deleted
    }

    /** 🔄 the push queue. Tombstones are in it on purpose — the delete has to travel too. */
    fun selectDirtyNotes(limit: Int): List<Note> =
        queries.selectDirtyNoteIds(userId, limit.toLong()).executeAsList()
            .mapNotNull { selectNoteByIdIncludingDeleted(it) }

    fun countDirtyNotes(): Long = queries.countDirtyNotes(userId).executeAsOne()

    /** 🖼️ the media backfill queue — notes whose bytes are on the server but not on this device. */
    fun selectNoteIdsNeedingMedia(limit: Int): List<String> =
        queries.selectNoteIdsNeedingMedia(limit.toLong()).executeAsList()

    /** 🖼️ the media UPLOAD queue — notes holding a file this device has and the server does not.
     *  Independent of the dirty-note queue on purpose: a file attached to an already-synced note,
     *  or one whose first upload failed, is still pending and must be retried. */
    fun selectNoteIdsNeedingUpload(limit: Int): List<String> =
        queries.selectNoteIdsNeedingUpload(userId, limit.toLong()).executeAsList()

    /** 🖼️ a file just gained an assetId — the note has to travel again so the id reaches the
     *  server. No-op on a note that is already dirty or already a tombstone. */
    fun markNoteDirtyForMedia(id: String) = queries.markNoteDirtyForMedia(id)

    /** 🔄 only the server may mark a note clean, and only with the version IT accepted.
     *  🔄 28-Aug-2026 — [pushedVersion] is what the note went up with; a row that has moved past it
     *  was edited mid-push and stays dirty, so the newer edit still gets its turn. */
    fun markNoteSynced(id: String, pushedVersion: String, acceptedVersion: String, serverUpdatedAt: Long?) =
        queries.markNoteSynced(
            pushedVersion = pushedVersion,
            acceptedVersion = acceptedVersion,
            serverUpdatedAt = serverUpdatedAt,
            id = id,
        )

    /** 🔄 writes a note that came FROM the server. Deliberately does NOT bump version or set a
     *  dirty syncStatus — doing either would re-queue every pulled note and loop forever. */
    fun applyServerNote(note: Note) {
        database.transaction {
            queries.deleteNoteContentsForNote(note.id)
            queries.insertOrUpdateNote(
                id = note.id,
                title = note.title,
                updatedAt = note.updatedAt,
                createdAt = note.createdAt,
                categoryId = note.categoryId,
                ownerId = note.ownerId ?: userId,
                version = note.version,
                syncStatus = SYNC_STATUS_SYNCED,
                deleted = if (note.deleted) 1L else 0L,
                deletedAt = note.deletedAt,
                serverUpdatedAt = note.serverUpdatedAt,
            )
            if (!note.deleted) note.contents.forEach { insertOrUpdateNotesContent(it) }
        }
    }

    /** 🔄 the watermark and the page it describes are committed together, so a crash re-pulls
     *  rather than skips. [block] must do the note writes. */
    fun commitPulledPage(
        userId: String, lastPulledAt: Long, lastPulledId: String?, block: () -> Unit
    ) = database.transaction {
        block()
        queries.upsertSyncState(
            userId = userId,
            lastPulledAt = lastPulledAt,
            lastPulledId = lastPulledId,
            lastSyncedAt = getCurrentTimestamp(),
        )
    }

    fun readSyncWatermark(userId: String): Pair<Long, String?> =
        queries.selectSyncState(userId).executeAsOneOrNull()
            ?.let { it.lastPulledAt to it.lastPulledId } ?: (0L to null)

    fun resetSyncWatermark(userId: String) =
        queries.upsertSyncState(userId = userId, lastPulledAt = 0L, lastPulledId = null, lastSyncedAt = 0L)

    /** 🔄 safe only for tombstones we have already pulled PAST, so no pull can resurrect them. */
    fun purgeAckedTombstones(before: Long) = queries.purgeAckedTombstones(before)
    // 🔄 28-Aug-2026 — the note row and its contents commit TOGETHER. Written apart, a save
    //   published a version the contents had not reached yet: sync could read the row between the
    //   two writes, push stale contents and mark that version clean, losing the edit.
    fun insertOrUpdateNoteFromDb(note: Note, dirtyContentIds: Set<String>? = null) : Note {
        log_d("NoteDao insert", note)
        database.transaction {
            queries.insertOrUpdateNote(
                id = note.id,
                title = note.title,
                updatedAt = note.updatedAt,
                createdAt = note.createdAt,
                categoryId = note.categoryId,
                ownerId = note.ownerId ?: userId,
                version = note.version,
                syncStatus = note.syncStatus,
                deleted = if (note.deleted) 1L else 0L,
                // 🔄 20-Aug-2026 sync: INSERT OR REPLACE rewrites the WHOLE row — omitting these would
                //   silently null the tombstone stamp and the server clock on every ordinary save.
                deletedAt = note.deletedAt,
                serverUpdatedAt = note.serverUpdatedAt,
            )
            val contentsToWrite =
                if (dirtyContentIds == null) note.contents
                else note.contents.filter { it.id in dirtyContentIds }
            contentsToWrite.forEach { content ->
                insertOrUpdateNotesContent(content)
            }
        }
       log_d("NoteDao end ", note)
       return note
    }
    // 🔄 20-Aug-2026 sync: keeps the pre-tombstone contract — a deleted note reads as absent,
    //   exactly as it did when delete was a hard DELETE. Sync uses the ...IncludingDeleted variant.
    fun selectNoteById(id: String): Note? =
        selectNoteByIdIncludingDeleted(id)?.takeIf { !it.deleted }

    fun selectNoteByIdIncludingDeleted(id: String): Note? {
        val rows = queries.selectById(id, userId).executeAsList()
        val noteWithContent = rows.firstOrNull()?.let { note ->
            Note(
                id = note.noteId,
                categoryId = note.categoryId,
                title = note.title,
                createdAt = note.noteCreatedAt,
                updatedAt = note.noteUpdatedAt,
                ownerId = note.ownerId ?: userId,
                version = note.version,
                syncStatus = note.syncStatus,
                deleted = note.deleted == 1L,
                deletedAt = note.deletedAt,
                serverUpdatedAt = note.serverUpdatedAt,
                contents = rows.mapNotNull { row ->
                    if (row.contentId != null&&!row.type.isNullOrEmpty()) {
                        val type = ContentType.valueOf(row.type)
                        when  {
                           type == ContentType.TEXT ->
                                TextContent(
                                    id = row.contentId,
                                    noteId = row.noteId,
                                    text =  row.text!!,
                                    position = row.position!!,
                                    createdAt = row.contentCreatedAt,
                                    updatedAt = row.contentUpdatedAt,
                                    metadata = row.metaData,
                                )

                            type.isDoc() || type.isMedia()  ->MediaContent(
                                title = row.contentTitle?:"${row.type}-${row.position}",
                                id = row.contentId,
                                noteId = row.noteId,
                                url = row.url!!,
                                localPath = row.localPath,
                                position = row.position!!,
                                createdAt = row.contentCreatedAt,
                                updatedAt = row.contentUpdatedAt,
                                duration = row.duration?:0,
                                type = type,
                                mimeType = row.mimeType ?: "",
                                sizeBytes = row.sizeBytes ?: 0,
                                width = row.width?.toInt() ?: 0,
                                height = row.height?.toInt() ?: 0,
                                thumbnailPath = row.thumbnailPath,
                                totalPages = (row.totalPages ?: 0L).toInt(),
                                progressPage = (row.progressPage ?: 0L).toInt(),
                                assetId = row.assetId,
                                checksum = row.checksum,
                            )

                          type  == ContentType.LINK-> Link(
                                url = row.url!!,
                                id = row.contentId,
                                noteId = row.noteId,
                                position = row.position!!,
                                createdAt = row.contentCreatedAt,
                                updatedAt = row.contentUpdatedAt,
                            )

                         type ==   ContentType.LOCATION -> Location(
                                latitude = row.lat!!,
                                longitude = row.long!!,
                                address = row.address,
                                position = row.position!!,
                                id = row.contentId,
                                noteId = row.noteId,
                                createdAt = row.contentCreatedAt,
                                updatedAt = row.contentUpdatedAt,
                            )

                            else -> null
                        }

                    } else null
                }
            )
        }
        return noteWithContent
    }
}
