package com.app.pustakam.core.media.download

import com.app.pustakam.core.filesys.platform.DirectoryManager
import com.app.pustakam.core.filesys.platform.FileDeleter
import com.app.pustakam.core.filesys.platform.FileWriter
import com.app.pustakam.core.filesys.platform.StoragePaths

// 📥 the real disk seam — partial bytes land in a ".part" next to where the file will finally live
class FileMediaPartStore(
    private val fileWriter: FileWriter,
    private val directories: DirectoryManager,
    private val deleter: FileDeleter,
    private val storagePaths: StoragePaths,
) : MediaPartStore {

    override fun bytesOnDisk(partRelativePath: String): Long =
        if (directories.exists(partRelativePath)) directories.sizeOf(partRelativePath) else 0

    override fun append(partRelativePath: String, bytes: ByteArray): Boolean =
        fileWriter.append(partRelativePath, bytes)

    // 📥 a rename, never a read-and-rewrite — a 100 MB video must not pass through memory twice
    override fun promote(partRelativePath: String, destinationRelativePath: String): Boolean =
        fileWriter.move(partRelativePath, destinationRelativePath)

    override fun discard(partRelativePath: String) {
        deleter.delete(partRelativePath)
    }

    override fun absolutePathOf(relativePath: String): String = storagePaths.toAbsolute(relativePath)
}
