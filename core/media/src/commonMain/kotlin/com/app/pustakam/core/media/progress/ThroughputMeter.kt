package com.app.pustakam.core.media.progress

private const val DEFAULT_SMOOTHING = 0.3
private const val MILLIS_PER_SECOND = 1000.0

// ⏱️ turns "bytes at time t" samples into a steady speed — pure, so the ETA is unit-testable
class ThroughputMeter(private val smoothing: Double = DEFAULT_SMOOTHING) {

    private var lastBytes = -1L
    private var lastAtMillis = 0L
    private var smoothed = 0.0

    // ⏱️ exponential moving average: one slow chunk must not make the ETA jump to minutes
    fun sample(bytes: Long, atMillis: Long): Long {
        if (lastBytes < 0 || bytes < lastBytes || atMillis <= lastAtMillis) {
            lastBytes = bytes
            lastAtMillis = atMillis
            return bytesPerSecond()
        }
        val seconds = (atMillis - lastAtMillis) / MILLIS_PER_SECOND
        val instant = (bytes - lastBytes) / seconds
        smoothed = if (smoothed <= 0.0) instant else smoothed + smoothing * (instant - smoothed)
        lastBytes = bytes
        lastAtMillis = atMillis
        return bytesPerSecond()
    }

    fun bytesPerSecond(): Long = if (smoothed <= 0.0) 0L else smoothed.toLong()

    // ⏱️ a pause makes the old speed a lie, so resuming starts measuring again
    fun reset() {
        lastBytes = -1L
        lastAtMillis = 0L
        smoothed = 0.0
    }
}
