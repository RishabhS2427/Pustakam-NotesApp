package com.app.pustakam.core.media.download

// 📥 the disk seam — partial bytes live in a ".part" file until the whole asset has landed
interface MediaPartStore {
    // 📥 how much of this asset is already here, which is exactly the Range offset to ask for
    fun bytesOnDisk(partRelativePath: String): Long

    fun append(partRelativePath: String, bytes: ByteArray): Boolean

    // 📥 the .part becomes the real file only when the transfer finished, so a half file is never playable
    fun promote(partRelativePath: String, destinationRelativePath: String): Boolean

    fun discard(partRelativePath: String)

    // 📥 absolute, because that is the form every playback call site expects
    fun absolutePathOf(relativePath: String): String
}
