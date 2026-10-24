package io.github.maniramezan.kommon.speech

/**
 * Maps `RecognitionListener.onRmsChanged` decibel values to a `0..1` loudness.
 *
 * Android documents the value as a rough dB reading and recognizers typically report between
 * about -2 dB (silence) and 10 dB (loud speech), so that is the range assumed here.
 */
internal object AudioLevelMapper {
    /** The decibel value treated as silence (level `0`). */
    const val SILENCE_DB: Float = -2f

    /** The decibel value treated as full scale (level `1`). */
    const val LOUD_DB: Float = 10f

    /** Linearly maps [rmsDb] from [SILENCE_DB]..[LOUD_DB] onto `0..1`, clamping outside values. */
    fun level(rmsDb: Float): Float {
        if (rmsDb.isNaN()) return 0f
        return ((rmsDb - SILENCE_DB) / (LOUD_DB - SILENCE_DB)).coerceIn(0f, 1f)
    }
}
