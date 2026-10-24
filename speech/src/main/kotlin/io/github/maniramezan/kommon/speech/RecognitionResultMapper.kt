package io.github.maniramezan.kommon.speech

import android.speech.RecognitionPart

/**
 * One word reported by Android 14's `RecognitionPart`, reduced to plain values.
 *
 * @property text The raw word text.
 * @property confidenceLevel One of the `RecognitionPart.CONFIDENCE_LEVEL_*` constants.
 * @property timestampMillis Milliseconds from the start of the audio at which the word begins.
 */
internal data class RecognitionPartData(
    val text: String,
    val confidenceLevel: Int,
    val timestampMillis: Long,
)

/** Builds [RecognizedUtterance]s from the pieces of a `SpeechRecognizer` result bundle. */
internal object RecognitionResultMapper {
    private val WHITESPACE = Regex("\\s+")
    private const val CONFIDENCE_HIGH = 1f
    private const val CONFIDENCE_MEDIUM_HIGH = 0.8f
    private const val CONFIDENCE_MEDIUM = 0.6f
    private const val CONFIDENCE_MEDIUM_LOW = 0.4f
    private const val CONFIDENCE_LOW = 0.2f

    /**
     * Maps a `RecognitionPart.CONFIDENCE_LEVEL_*` value to a `0..1` confidence, or `null` for
     * `CONFIDENCE_LEVEL_UNKNOWN` and any unrecognized level.
     */
    fun confidence(level: Int): Float? =
        when (level) {
            RecognitionPart.CONFIDENCE_LEVEL_HIGH -> CONFIDENCE_HIGH
            RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_HIGH -> CONFIDENCE_MEDIUM_HIGH
            RecognitionPart.CONFIDENCE_LEVEL_MEDIUM -> CONFIDENCE_MEDIUM
            RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW -> CONFIDENCE_MEDIUM_LOW
            RecognitionPart.CONFIDENCE_LEVEL_LOW -> CONFIDENCE_LOW
            else -> null
        }

    /**
     * Builds the final utterance for a recognition result.
     *
     * The best transcription is the first of [results]; the rest become alternatives. When [parts]
     * are present they supply per-word text, confidence, and (if [includeTiming]) start and
     * duration, where a word's duration runs to the next word's start. Otherwise words are the
     * whitespace-separated pieces of the best transcription and each carries the transcription's
     * score from [confidenceScores], if reported.
     *
     * @return `null` when there is no text at all.
     */
    fun utterance(
        results: List<String>?,
        confidenceScores: FloatArray?,
        parts: List<RecognitionPartData>?,
        includeTiming: Boolean,
    ): RecognizedUtterance? {
        val candidates = results.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val usableParts = parts.orEmpty().filter { it.text.isNotBlank() }
        val text = candidates.firstOrNull() ?: usableParts.joinToString(" ") { it.text.trim() }
        if (text.isEmpty()) return null

        val words =
            if (usableParts.isNotEmpty()) {
                wordsFromParts(usableParts, includeTiming)
            } else {
                val score = confidenceScores?.firstOrNull()?.takeIf { it in 0f..1f }
                text.split(WHITESPACE).filter { it.isNotEmpty() }.map { RecognizedWord(it, score) }
            }
        val alternatives = candidates.drop(1).filter { it != text }.distinct()
        return RecognizedUtterance(text = text, words = words, alternatives = alternatives)
    }

    private fun wordsFromParts(
        parts: List<RecognitionPartData>,
        includeTiming: Boolean,
    ): List<RecognizedWord> =
        parts.mapIndexed { index, part ->
            val start = part.timestampMillis.takeIf { includeTiming && it >= 0 }
            val nextStart = parts.getOrNull(index + 1)?.timestampMillis?.takeIf { includeTiming && it >= 0 }
            val duration = if (start != null && nextStart != null && nextStart >= start) nextStart - start else null
            RecognizedWord(
                text = part.text.trim(),
                confidence = confidence(part.confidenceLevel),
                startMillis = start,
                durationMillis = duration,
            )
        }
}
