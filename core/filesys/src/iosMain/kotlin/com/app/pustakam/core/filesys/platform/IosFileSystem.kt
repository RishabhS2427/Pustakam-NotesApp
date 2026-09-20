@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.app.pustakam.core.filesys.platform

import com.app.pustakam.core.filesys.mime.MimeCatalog
import com.app.pustakam.core.filesys.model.FileMetadata
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** Documents directory — matches iOS FileOps.swift getDocumentsDirectory(). */
internal fun documentsRoot(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String

internal fun resolveInStorage(relativePath: String): String =
    "${documentsRoot()}/${relativePath.trimStart('/')}"

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}


private fun ByteArray.toNSData(): NSData = memScoped {
    @OptIn(BetaInteropApi::class)
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

class IosFileReader : FileReader {

    override fun read(relativePath: String): ByteArray? =
        NSData.dataWithContentsOfFile(resolveInStorage(relativePath))?.toByteArray()

    override fun readAbsolute(absolutePath: String): ByteArray? =
        NSData.dataWithContentsOfFile(absolutePath)?.toByteArray()

    override fun readText(relativePath: String, maxBytes: Long): String? {
        val bytes = read(relativePath) ?: return null
        val capped = if (maxBytes in 1 until bytes.size.toLong()) {
            bytes.copyOf(maxBytes.toInt())
        } else {
            bytes
        }
        return capped.decodeToString()
    }
}

class IosFileWriter : FileWriter {
    override fun write(relativePath: String, bytes: ByteArray): Boolean {
        val absolute = resolveInStorage(relativePath)
        IosDirectoryManager().ensure(relativePath.substringBeforeLast('/', ""))
        return bytes.toNSData().writeToFile(absolute, atomically = true)
    }
}

class IosDirectoryManager : DirectoryManager {

    private val manager get() = NSFileManager.defaultManager

    override fun ensure(folder: String): Boolean {
        if (folder.isEmpty()) return true
        val absolute = resolveInStorage(folder)
        if (manager.fileExistsAtPath(absolute)) return true
        return manager.createDirectoryAtPath(
            path = absolute,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    override fun exists(relativePath: String): Boolean =
        manager.fileExistsAtPath(resolveInStorage(relativePath))

    override fun list(folder: String): List<String> =
        (manager.contentsOfDirectoryAtPath(resolveInStorage(folder), null))
            ?.filterIsInstance<String>()
            .orEmpty()

    override fun sizeOf(relativePath: String): Long {
        val attributes = manager.attributesOfItemAtPath(resolveInStorage(relativePath), null)
        return (attributes?.get(NSFileSize) as? Number)?.toLong() ?: 0L
    }
}

/** [sourceHandle] is a file URL string from the document picker (already copied by `asCopy`). */
class IosFileCopier : FileCopier {
    override fun copyIn(sourceHandle: String, destinationRelativePath: String): Boolean {
        val manager = NSFileManager.defaultManager
        val destination = resolveInStorage(destinationRelativePath)
        IosDirectoryManager().ensure(destinationRelativePath.substringBeforeLast('/', ""))
        val sourcePath = NSURL.URLWithString(sourceHandle)?.path ?: sourceHandle
        if (manager.fileExistsAtPath(destination)) manager.removeItemAtPath(destination, null)
        return manager.copyItemAtPath(sourcePath, toPath = destination, error = null)
    }
}

class IosFileDeleter : FileDeleter {
    override fun delete(relativePath: String): Boolean {
        val manager = NSFileManager.defaultManager
        val absolute = resolveInStorage(relativePath)
        // Already gone counts as success — matches iOS FileOps.swift deleteFile().
        if (!manager.fileExistsAtPath(absolute)) return true
        return manager.removeItemAtPath(absolute, null)
    }
}

class IosMetadataReader : MetadataReader {

    override fun describe(relativePath: String): FileMetadata? {
        val manager = NSFileManager.defaultManager
        val absolute = resolveInStorage(relativePath)
        if (!manager.fileExistsAtPath(absolute)) return null
        val name = relativePath.substringAfterLast('/')
        val type = MimeCatalog.contentTypeFor(name)
        return FileMetadata(
            fileName = name,
            extension = name.substringAfterLast('.', ""),
            mimeType = MimeCatalog.mimeFor(type),
            contentType = type,
            sizeBytes = IosDirectoryManager().sizeOf(relativePath),
        )
    }

    override fun describeHandle(sourceHandle: String): FileMetadata? {
        val path = NSURL.URLWithString(sourceHandle)?.path ?: sourceHandle
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(path)) return null
        val name = path.substringAfterLast('/')
        val type = MimeCatalog.contentTypeFor(name)
        val size = (manager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? Number)
            ?.toLong() ?: 0L
        return FileMetadata(
            fileName = name,
            extension = name.substringAfterLast('.', ""),
            mimeType = MimeCatalog.mimeFor(type),
            contentType = type,
            sizeBytes = size,
        )
    }
}

// 🖼️ 20-Aug-2026 sync: recomputed per call on purpose — the container UUID changes on every update,
//   so a cached root would go stale exactly like the absolute paths resolveLocalFilePath re-anchors.
class IosStoragePaths : StoragePaths {
    override fun rootPath(): String = documentsRoot()
}
