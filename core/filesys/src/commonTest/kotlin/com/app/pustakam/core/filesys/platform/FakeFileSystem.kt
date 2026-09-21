package com.app.pustakam.core.filesys.platform

import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.filesys.model.FileMetadata
import com.app.pustakam.core.filesys.model.ThumbnailPolicy
import com.app.pustakam.core.filesys.model.ThumbnailRequest
import com.app.pustakam.core.filesys.model.ThumbnailResult

// 🔧 30-Jul-2026 02:10 Phase 3 — in-memory implementations of every seam.
//   Their existence IS the point of the interfaces: from Phase 4 on, any shared component that
//   touches files can be tested with no platform, no disk and no Robolectric. Writing this fake is
//   also what caught the FileReader.read / MetadataReader.read signature collision.
class FakeFileSystem(
    initial: Map<String, ByteArray> = emptyMap(),
) : FileReader, FileWriter, DirectoryManager, FileCopier, FileDeleter, MetadataReader {

    /** relativePath -> bytes */
    val files: MutableMap<String, ByteArray> = initial.toMutableMap()

    /** Handles that live "outside" app storage — a SAF uri or a picked file URL. */
    val external: MutableMap<String, ByteArray> = mutableMapOf()

    private val folders: MutableSet<String> = mutableSetOf()

    // ---- FileReader ----

    override fun read(relativePath: String): ByteArray? = files[relativePath]

    // 🖼️ a handle that lives outside app storage — the SAF/picked-file case
    override fun readAbsolute(absolutePath: String): ByteArray? =
        external[absolutePath] ?: files[absolutePath]

    override fun readText(relativePath: String, maxBytes: Long): String? {
        val bytes = files[relativePath] ?: return null
        val capped =
            if (maxBytes in 1 until bytes.size.toLong()) bytes.copyOf(maxBytes.toInt()) else bytes
        return capped.decodeToString()
    }

    // ---- FileWriter ----

    override fun write(relativePath: String, bytes: ByteArray): Boolean {
        folders += relativePath.substringBeforeLast('/', "")
        files[relativePath] = bytes
        return true
    }

    override fun append(relativePath: String, bytes: ByteArray): Boolean =
        write(relativePath, (files[relativePath] ?: ByteArray(0)) + bytes)

    override fun move(fromRelativePath: String, toRelativePath: String): Boolean {
        val bytes = files.remove(fromRelativePath) ?: return false
        return write(toRelativePath, bytes)
    }

    // ---- DirectoryManager ----

    override fun ensure(folder: String): Boolean {
        folders += folder
        return true
    }

    override fun exists(relativePath: String): Boolean =
        files.containsKey(relativePath) || relativePath in folders

    override fun list(folder: String): List<String> =
        files.keys
            .filter { it.substringBeforeLast('/', "") == folder }
            .map { it.substringAfterLast('/') }

    override fun sizeOf(relativePath: String): Long = files[relativePath]?.size?.toLong() ?: 0L

    // ---- FileCopier ----

    override fun copyIn(sourceHandle: String, destinationRelativePath: String): Boolean {
        val bytes = external[sourceHandle] ?: return false
        files[destinationRelativePath] = bytes
        return true
    }

    // ---- FileDeleter ----

    override fun delete(relativePath: String): Boolean {
        files.remove(relativePath)
        return true // already-gone counts as success on both platforms
    }

    // ---- MetadataReader ----

    override fun describe(relativePath: String): FileMetadata? {
        val bytes = files[relativePath] ?: return null
        return metadata(relativePath.substringAfterLast('/'), bytes.size.toLong())
    }

    override fun describeHandle(sourceHandle: String): FileMetadata? {
        val bytes = external[sourceHandle] ?: return null
        return metadata(sourceHandle.substringAfterLast('/'), bytes.size.toLong())
    }

    private fun metadata(name: String, size: Long): FileMetadata {
        val type = MimeCatalog.contentTypeFor(name)
        return FileMetadata(
            fileName = name,
            extension = name.substringAfterLast('.', ""),
            mimeType = MimeCatalog.mimeFor(type),
            contentType = type,
            sizeBytes = size,
        )
    }
}

/** Records what it was asked for; generates nothing. */
class FakeThumbnailGenerator : ThumbnailGenerator {
    val requests: MutableList<ThumbnailRequest> = mutableListOf()
    var result: ThumbnailResult? = null

    override fun generate(request: ThumbnailRequest): ThumbnailResult {
        requests += request
        if (!ThumbnailPolicy.isEligible(request.contentType)) return ThumbnailResult.NotSupported
        return result ?: ThumbnailResult.Generated(request.destinationRelativePath)
    }
}

/** Fixed page counts, keyed by path. */
class FakeDocumentRenderer(private val pages: Map<String, Int> = emptyMap()) : DocumentRenderer {
    override fun pageCount(relativePath: String): Int = pages[relativePath] ?: 0
}

/**
 * Deterministic text measurement: [linesFor] characters per line, [lineHeight] points per line.
 * This is what lets ExportLayoutBuilder be proven to paginate identically on both platforms.
 */
class FakeTextMeasurer(
    private val charsPerLine: Int = 50,
    private val lineHeight: Float = 20f,
) : TextMeasurer {
    override fun measureHeight(text: String, style: TextStyle, width: Float): Float {
        if (text.isEmpty() || width <= 0f) return 0f
        val lines = (text.length + charsPerLine - 1) / charsPerLine
        return lines * lineHeight * (if (style.bold) 1.2f else 1f)
    }
}
