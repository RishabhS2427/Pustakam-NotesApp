package com.app.pustakam.core.media.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThroughputMeterTest {

    @Test
    fun `first sample cannot know a speed yet`() {
        val meter = ThroughputMeter()
        assertEquals(0L, meter.sample(bytes = 1000, atMillis = 0))
    }

    @Test
    fun `a steady stream settles on its real speed`() {
        val meter = ThroughputMeter()
        meter.sample(0, 0)
        // ⏱️ 100 KB every 100 ms is 1 MB/s; the average converges on it
        var at = 0L
        var bytes = 0L
        repeat(40) {
            at += 100
            bytes += 100 * 1024
            meter.sample(bytes, at)
        }
        val speed = meter.bytesPerSecond()
        assertTrue(speed in 900_000..1_100_000, "settled on $speed")
    }

    // ⏱️ one stalled chunk must not throw the ETA into minutes
    @Test
    fun `a single slow chunk barely moves the average`() {
        val meter = ThroughputMeter()
        meter.sample(0, 0)
        var at = 0L
        var bytes = 0L
        repeat(20) {
            at += 100
            bytes += 100 * 1024
            meter.sample(bytes, at)
        }
        val before = meter.bytesPerSecond()
        at += 1000
        bytes += 1024
        val after = meter.sample(bytes, at)
        assertTrue(after > before / 2, "$after collapsed from $before")
    }

    @Test
    fun `time going backwards is ignored rather than trusted`() {
        val meter = ThroughputMeter()
        meter.sample(0, 1000)
        meter.sample(5000, 500)
        assertEquals(0L, meter.bytesPerSecond())
    }

    @Test
    fun `reset forgets the old speed`() {
        val meter = ThroughputMeter()
        meter.sample(0, 0)
        meter.sample(1024 * 1024, 1000)
        assertTrue(meter.bytesPerSecond() > 0)
        meter.reset()
        assertEquals(0L, meter.bytesPerSecond())
    }
}
