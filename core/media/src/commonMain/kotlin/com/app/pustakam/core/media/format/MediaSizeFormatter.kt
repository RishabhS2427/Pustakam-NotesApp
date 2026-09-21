package com.app.pustakam.core.media.format

private const val UNIT = 1024L
private const val KB = UNIT
private const val MB = KB * UNIT
private const val GB = MB * UNIT
private const val TB = GB * UNIT
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val NO_DECIMALS_ABOVE = 100L

// 📏 byte and time wording lives HERE and only here, so Android and iOS cannot drift apart
object MediaSizeFormatter {

    // 📏 "0 B", "820 B", "1.2 KB", "4.5 MB", "125 MB" — one decimal until the number gets wide
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        return when {
            bytes < KB -> "$bytes B"
            bytes < MB -> scaled(bytes, KB, "KB")
            bytes < GB -> scaled(bytes, MB, "MB")
            bytes < TB -> scaled(bytes, GB, "GB")
            else -> scaled(bytes, TB, "TB")
        }
    }

    // ⏱️ "12s", "1m 05s", "2h 03m" — blank when it cannot be known yet
    fun formatEta(seconds: Long): String {
        if (seconds < 0) return ""
        return when {
            seconds < SECONDS_PER_MINUTE -> "${seconds}s"
            seconds < SECONDS_PER_HOUR ->
                "${seconds / SECONDS_PER_MINUTE}m ${pad(seconds % SECONDS_PER_MINUTE)}s"
            else ->
                "${seconds / SECONDS_PER_HOUR}h ${pad((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)}m"
        }
    }

    // 📏 "4.5 MB/s" — the same scale as every other size on the card
    fun formatSpeed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "" else "${formatBytes(bytesPerSecond)}/s"

    fun formatPercent(percent: Int): String = "$percent%"

    private fun scaled(bytes: Long, unit: Long, suffix: String): String {
        val whole = bytes / unit
        if (whole >= NO_DECIMALS_ABOVE) return "$whole $suffix"
        val tenths = (bytes % unit) * 10 / unit
        return "$whole.$tenths $suffix"
    }

    private fun pad(value: Long): String = if (value < 10) "0$value" else "$value"
}
