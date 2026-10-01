package com.app.pustakam.core.drawing.brush

class DrawRandom(seed: Int) {

    private var state: Int = if (seed == 0) FALLBACK_SEED else seed

    fun nextFloat(): Float {
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        return (state ushr 8) / UNIT_DIVISOR
    }

    fun nextSigned(): Float = nextFloat() * 2f - 1f

    private companion object {
        const val FALLBACK_SEED = 0x2545F491
        const val UNIT_DIVISOR = 16777216f
    }
}
