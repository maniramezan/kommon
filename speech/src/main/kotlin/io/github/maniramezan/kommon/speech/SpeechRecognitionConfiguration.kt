package io.github.maniramezan.kommon.speech

import java.util.Locale

/**
 * Settings for one speech recognition run.
 *
 * @property locale The language to recognize.
 * @property preferOnDevice Use on-device recognition when it is available, falling back to the
 *   system recognizer's offline preference otherwise.
 * @property reportsAlternatives Whether to report other plausible transcriptions.
 * @property requestsWordDetails Whether to request per-word confidence and timing where the
 *   platform supports it (Android 14 and later).
 * @property completeSilenceMillis A hint for how long the speaker must be silent before the input
 *   is considered complete, or `null` to use the recognizer's default.
 * @property maxAlternatives The maximum number of transcriptions to request when
 *   [reportsAlternatives] is true; the best one plus alternatives.
 */
public data class SpeechRecognitionConfiguration(
    val locale: Locale,
    val preferOnDevice: Boolean = true,
    val reportsAlternatives: Boolean = true,
    val requestsWordDetails: Boolean = true,
    val completeSilenceMillis: Long? = null,
    val maxAlternatives: Int = DEFAULT_MAX_ALTERNATIVES,
) {
    init {
        require(maxAlternatives >= 1) { "maxAlternatives must be at least 1" }
        require(completeSilenceMillis == null || completeSilenceMillis >= 0) { "completeSilenceMillis must not be negative" }
    }

    private companion object {
        const val DEFAULT_MAX_ALTERNATIVES = 3
    }
}
