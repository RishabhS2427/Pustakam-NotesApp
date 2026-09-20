package com.app.pustakam.core.filesys.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.filesys.model.FileMetadata
import java.io.File

// 🔧 30-Jul-2026 02:10 Phase 3 — Android bindings for the file-IO seams. java.io only; every path
//   is resolved against context.filesDir, which is exactly where the app already keeps its files,
//   so these read and write the SAME locations the inline code did.

/** Shared root resolution — filesDir, matching every existing Android call site. */
internal fun Context.resolveInStorage(relativePath: String): File =
    File(filesDir, relativePath.trimStart('/'))

class AndroidFileReader(private val context: Context) : FileReader {

    override fun read(relativePath: String): ByteArray? = try {
        val file = context.resolveInStorage(relativePath)
        if (file.exists() && file.isFile) file.readBytes() else null
    } catch (e: Exception) {
        e.printStackTrace(); null
    }

    override fun readAbsolute(absolutePath: String): ByteArray? = try {
        val file = File(absolutePath)
        if (file.exists() && file.isFile) file.readBytes() else null
    } catch (e: Exception) {
        e.printStackTrace(); null
    }

    override fun readText(relativePath: String, maxBytes: Long): String? = try {
        val file = context.resolveInStorage(relativePath)
        if (!file.exists() || !file.isFile) null
        else file.inputStream().use { stream ->
            // 🔧 30-Jul-2026 02:10 same truncation the book reader already applied
            val bytes = stream.readBytes()
            val capped = if (maxBytes in 1 until bytes.size.toLong()) {
                bytes.copyOf(maxBytes.toInt())
            } else {
                bytes
            }
            capped.decodeToString()
        }
    } catch (e: Exception) {
        e.printStackTrace(); null
    }
}

class AndroidFileWriter(private val context: Context) : FileWriter {
    override fun write(relativePath: String, bytes: ByteArray): Boolean = try {
        val file = context.resolveInStorage(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        true
    } catch (e: Exception) {
        e.printStackTrace(); false
    }
}

class AndroidDirectoryManager(private val context: Context) : DirectoryManager {

    override fun ensure(folder: String): Boolean = try {
        val dir = context.resolveInStorage(folder)
        dir.exists() || dir.mkdirs()
    } catch (e: Exception) {
        e.printStackTrace(); false
    }

    override fun exists(relativePath: String): Boolean =
        context.resolveInStorage(relativePath).exists()

    override fun list(folder: String): List<String> =
        context.resolveInStorage(folder).listFiles()?.map { it.name }.orEmpty()

    override fun sizeOf(relativePath: String): Long =
        context.resolveInStorage(relativePath).takeIf { it.exists() }?.length() ?: 0L
}

/** [sourceHandle] is a SAF content:// uri. */
class AndroidFileCopier(private val context: Context) : FileCopier {
    override fun copyIn(sourceHandle: String, destinationRelativePath: String): Boolean = try {
        val destination = context.resolveInStorage(destinationRelativePath)
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(Uri.parse(sourceHandle))?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
    } catch (e: Exception) {
        e.printStackTrace(); false
    }
}

class AndroidFileDeleter(private val context: Context) : FileDeleter {
    override fun delete(relativePath: String): Boolean = try {
        val file = context.resolveInStorage(relativePath)
        // Already gone counts as success — matches the existing deleteFile() behaviour.
        !file.exists() || file.delete()
    } catch (e: Exception) {
        e.printStackTrace(); false
    }
}

class AndroidMetadataReader(private val context: Context) : MetadataReader {

    override fun describe(relativePath: String): FileMetadata? {
        val file = context.resolveInStorage(relativePath)
        if (!file.exists() || !file.isFile) return null
        val type = MimeCatalog.contentTypeFor(file.name)
        return FileMetadata(
            fileName = file.name,
            extension = file.extension,
            mimeType = MimeCatalog.mimeFor(type),
            contentType = type,
            sizeBytes = file.length(),
        )
    }

    /** 🔧 30-Jul-2026 02:10 the OpenableColumns query lifted from FileImportManager.importUris. */
    override fun describeHandle(sourceHandle: String): FileMetadata? = try {
        val uri = Uri.parse(sourceHandle)
        var name = ""
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val mime = context.contentResolver.getType(uri)
        val type = MimeCatalog.contentTypeFor(name, mime)
        FileMetadata(
            fileName = name,
            extension = name.substringAfterLast('.', ""),
            mimeType = mime,
            contentType = type,
            sizeBytes = size,
        )
    } catch (e: Exception) {
        e.printStackTrace(); null
    }
}

// 🖼️ 20-Aug-2026 sync: filesDir is the root every Android call site already resolves against
class AndroidStoragePaths(private val context: Context) : StoragePaths {
    override fun rootPath(): String = context.filesDir.absolutePath
}
