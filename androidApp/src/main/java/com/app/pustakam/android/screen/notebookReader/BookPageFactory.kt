package com.app.pustakam.android.screen.notebookReader

// 🔧 19-Jul-2026: NEW (DRY refactor) — page building extracted from BookReaderViewModel so the
//   full-screen reader AND the inline editor book widget build pages through the SAME functions.
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.app.pustakam.core.model.models.response.notes.Note
import com.app.pustakam.core.model.models.response.notes.NoteContentModel
import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.filesys.pagination.BookPaginator
import com.app.pustakam.core.filesys.pagination.BookPaginator.MAX_TEXT_FILE_BYTES
import java.io.File

object BookPageFactory {

    // 🔧 19-Jul-2026: whole-note book — cover + every content (moved from the ViewModel, unchanged)
    fun buildForNote(note: Note): List<BookPage> {
        val pages = mutableListOf<BookPage>()
        val contents = note.contents.sortedBy { it.position }
        pages.add(
            BookPage.Cover(
                title = note.title?.ifBlank { "Untitled note" } ?: "Untitled note",
                subtitle = "${contents.size} entries",
            )
        )
        contents.forEach { content -> pages.addAll(pagesFor(content)) }
        return pages
    }

    // 🔧 19-Jul-2026: single-file book — ONLY the tapped document's pages (fix: opening the 2nd
    //   file no longer shows the 1st file's pages first). No cover, straight into the file.
    fun buildForContent(content: NoteContentModel): List<BookPage> = pagesFor(content)

    // 🔧 19-Jul-2026: ONE content → its pages; the shared core both builders call (DRY)
    fun pagesFor(content: NoteContentModel): List<BookPage> = when (content) {
        is NoteContentModel.TextContent -> paginate(content.text, content.id)

        is NoteContentModel.MediaContent -> when (content.type) {
            ContentType.IMAGE, ContentType.GIF ->
                listOf(BookPage.ImagePage(content.localPath ?: content.url, content.title, content.id))

            ContentType.VIDEO, ContentType.AUDIO -> listOf(BookPage.MediaPage(content, content.id))

            ContentType.PDF -> pdfSheets(content)

            ContentType.TXT, ContentType.MD -> textFilePages(content)

            else -> listOf(BookPage.DocFilePage(content, content.id))
        }

        is NoteContentModel.Link -> listOf(BookPage.LinkPage(content.url, content.id))

        is NoteContentModel.Location ->
            listOf(BookPage.LocationPage(content.latitude, content.longitude, content.address, content.id))

        is NoteContentModel.Drawing -> emptyList()
    }

    // 🔧 19-Jul-2026: word-boundary pagination — same cut preference as TextBlockSplitter
    // 🔧 30-Jul-2026 02:10 Phase 2 — algorithm moved VERBATIM to BookPaginator (shared with iOS).
    //   This wrapper only maps the shared chunk onto Android's BookPage.TextPage, so page counts —
    //   and therefore every persisted progressPage/totalPages — are unchanged.
    fun paginate(text: String, sourceId: String?): List<BookPage.TextPage> =
        BookPaginator.paginate(text).map { chunk ->
            BookPage.TextPage(chunk.text, chunk.pageNumber, chunk.totalPages, sourceId)
        }

    // 🔧 19-Jul-2026: one book sheet per PDF page; count here, bitmaps rendered lazily by the UI
    private fun pdfSheets(media: NoteContentModel.MediaContent): List<BookPage> {
        val path = media.localPath ?: return listOf(BookPage.DocFilePage(media, media.id))
        return try {
            val file = File(path)
            if (!file.exists()) return listOf(BookPage.DocFilePage(media, media.id))
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val count = PdfRenderer(descriptor).use { it.pageCount }
            (0 until count).map { BookPage.PdfSheet(path, it, count, media.title, media.id) }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(BookPage.DocFilePage(media, media.id))
        }
    }

    // 🔧 15-Aug-2026: shared with the inline document probe so both cap text files identically
    fun readCappedText(file: File): String = file.inputStream().use { stream ->
        stream.readBytes().let { bytes ->
            if (bytes.size > MAX_TEXT_FILE_BYTES) bytes.copyOf(MAX_TEXT_FILE_BYTES.toInt()) else bytes
        }.decodeToString()
    }

    // 🔧 19-Jul-2026: txt/md files become paginated text pages (md shown as plain text v1)
    private fun textFilePages(media: NoteContentModel.MediaContent): List<BookPage> {
        val path = media.localPath ?: return listOf(BookPage.DocFilePage(media, media.id))
        return try {
            val file = File(path)
            if (!file.exists()) return listOf(BookPage.DocFilePage(media, media.id))
            val text = readCappedText(file)
            paginate(text, media.id).ifEmpty { listOf(BookPage.DocFilePage(media, media.id)) }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(BookPage.DocFilePage(media, media.id))
        }
    }
}
