package io.github.maniramezan.kommon.speech

import java.util.Locale

/**
 * A word-level comparison between the text someone was asked to say and what a speech recognizer
 * heard.
 *
 * Use it for read-aloud exercises: show the reference sentence, record the speaker, and align the
 * transcript against the sentence to find matched, misheard, missed, and extra words:
 *
 * ```
 * val alignment = TranscriptAlignment.align(
 *     reference = "The quick brown fox",
 *     heard = listOf(TranscriptToken("the"), TranscriptToken("quick"), TranscriptToken("box")),
 * )
 * // alignment.tokens.map { it.status } is Match, Match, Omission, Substitution
 * alignment.tokens.map { it.status }
 * ```
 *
 * Words are compared after normalization (case-folded, punctuation stripped, curly apostrophes
 * straightened, hyphenated words split), so `"Don’t"` matches `"don't"`. Numbers aren't spelled
 * out, so a recognizer that writes `"3"` for a spoken "three" counts as a substitution.
 *
 * The alignment is a minimum-edit-distance alignment over words, so a single missed word doesn't
 * shift every following word into a mismatch. It measures what the recognizer understood, not
 * pronunciation itself. A recognizer's language model often repairs mildly mispronounced words,
 * so treat a clean alignment as "intelligible", not "perfect".
 *
 * @property tokens Every aligned position, in reading order.
 */
public data class TranscriptAlignment(
    val tokens: List<AlignedToken>,
) {
    /** The number of words in the reference text. */
    public val referenceWordCount: Int
        get() = tokens.count { it.reference != null }

    /** The number of positions with the given [status]. */
    public fun count(status: AlignedTokenStatus): Int = tokens.count { it.status == status }

    /**
     * The share of reference words that were heard at all (matched, low-confidence, or
     * substituted), from `0` to `1`. An empty reference counts as complete.
     */
    public val completeness: Double
        get() {
            val total = referenceWordCount
            if (total == 0) return 1.0
            return (total - count(AlignedTokenStatus.Omission)).toDouble() / total
        }

    /**
     * An overall score from `0` to `1`.
     *
     * Each [AlignedTokenStatus.Match] earns one point and each [AlignedTokenStatus.LowConfidence]
     * earns half a point. The total is divided by the number of reference words plus the number of
     * inserted words, so extra words lower the score. An empty reference with nothing heard
     * scores `1`.
     */
    public val score: Double
        get() {
            val denominator = referenceWordCount + count(AlignedTokenStatus.Insertion)
            if (denominator == 0) return 1.0
            val points = count(AlignedTokenStatus.Match) + count(AlignedTokenStatus.LowConfidence) * LOW_CONFIDENCE_CREDIT
            return points / denominator
        }

    public companion object {
        private const val LOW_CONFIDENCE_CREDIT = 0.5
        private const val DEFAULT_LOW_CONFIDENCE_THRESHOLD = 0.5f

        /**
         * Aligns heard words against a reference text.
         *
         * @param reference The text the speaker was asked to say.
         * @param heard The words the recognizer heard, in order.
         * @param lowConfidenceThreshold Matched words whose confidence is below this become
         *   [AlignedTokenStatus.LowConfidence]. Words without a confidence are never low.
         * @param locale The locale used for case folding.
         */
        public fun align(
            reference: String,
            heard: List<TranscriptToken>,
            lowConfidenceThreshold: Float = DEFAULT_LOW_CONFIDENCE_THRESHOLD,
            locale: Locale = Locale.US,
        ): TranscriptAlignment {
            val referenceWords = TranscriptNormalizer.words(reference)
            val heardWords =
                heard.flatMap { token ->
                    TranscriptNormalizer.words(token.text).map { TranscriptToken(it, token.confidence) }
                }
            val referenceKeys = referenceWords.map { TranscriptNormalizer.key(it, locale) }
            val heardKeys = heardWords.map { TranscriptNormalizer.key(it.text, locale) }

            val tokens =
                EditDistance.operations(referenceKeys, heardKeys).map { operation ->
                    when (operation) {
                        is EditDistance.Operation.Keep -> {
                            val word = heardWords[operation.heard]
                            val isLow = word.confidence?.let { it < lowConfidenceThreshold } ?: false
                            AlignedToken(
                                referenceWords[operation.reference],
                                word.text,
                                word.confidence,
                                if (isLow) AlignedTokenStatus.LowConfidence else AlignedTokenStatus.Match,
                            )
                        }
                        is EditDistance.Operation.Substitute -> {
                            val word = heardWords[operation.heard]
                            AlignedToken(
                                referenceWords[operation.reference],
                                word.text,
                                word.confidence,
                                AlignedTokenStatus.Substitution,
                            )
                        }
                        is EditDistance.Operation.Delete ->
                            AlignedToken(referenceWords[operation.reference], null, null, AlignedTokenStatus.Omission)
                        is EditDistance.Operation.Insert -> {
                            val word = heardWords[operation.heard]
                            AlignedToken(null, word.text, word.confidence, AlignedTokenStatus.Insertion)
                        }
                    }
                }
            return TranscriptAlignment(tokens)
        }

        /**
         * Aligns a plain heard transcript, with no confidences, against a reference text.
         *
         * @param reference The text the speaker was asked to say.
         * @param heard The recognized text.
         * @param locale The locale used for case folding.
         */
        public fun align(
            reference: String,
            heard: String,
            locale: Locale = Locale.US,
        ): TranscriptAlignment = align(reference, listOf(TranscriptToken(heard)), locale = locale)
    }
}
