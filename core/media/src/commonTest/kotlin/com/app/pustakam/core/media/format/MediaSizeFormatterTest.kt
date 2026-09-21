package com.app.pustakam.core.media.format

import kotlin.test.Test
import kotlin.test.assertEquals

// 📏 the wording table — both platforms print these exact strings, so the copy is pinned here
class MediaSizeFormatterTest {

    @Test
    fun `bytes scale the way a person reads them`() {
        val table = listOf(
            0L to "0 B",
            -1L to "0 B",
            512L to "512 B",
            1023L to "1023 B",
            1024L to "1.0 KB",
            1536L to "1.5 KB",
            1024L * 1024 to "1.0 MB",
            (1.5 * 1024 * 1024).toLong() to "1.5 MB",
            1024L * 1024 * 1024 to "1.0 GB",
            1024L * 1024 * 1024 * 1024 to "1.0 TB",
        )
        table.forEach { (bytes, expected) ->
            assertEquals(expected, MediaSizeFormatter.formatBytes(bytes), "for $bytes")
        }
    }

    // 📏 a decimal on a three-digit number is noise on a card, so it is dropped above 100
    @Test
    fun `wide numbers lose the decimal`() {
        assertEquals("99.5 MB", MediaSizeFormatter.formatBytes(104_333_312))
        assertEquals("125 MB", MediaSizeFormatter.formatBytes(131_072_000))
    }

    @Test
    fun `eta reads as a duration`() {
        assertEquals("", MediaSizeFormatter.formatEta(-1))
        assertEquals("0s", MediaSizeFormatter.formatEta(0))
        assertEquals("59s", MediaSizeFormatter.formatEta(59))
        assertEquals("1m 00s", MediaSizeFormatter.formatEta(60))
        assertEquals("1m 05s", MediaSizeFormatter.formatEta(65))
        assertEquals("59m 59s", MediaSizeFormatter.formatEta(3599))
        assertEquals("1h 00m", MediaSizeFormatter.formatEta(3600))
        assertEquals("2h 03m", MediaSizeFormatter.formatEta(7380))
    }

    @Test
    fun `speed is blank until it is known`() {
        assertEquals("", MediaSizeFormatter.formatSpeed(0))
        assertEquals("1.0 MB/s", MediaSizeFormatter.formatSpeed(1024 * 1024))
    }
}
