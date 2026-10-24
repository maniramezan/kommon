package io.github.maniramezan.kommon.speech

/** How one position of a [TranscriptAlignment] compares the expected text with what was heard. */
public enum class AlignedTokenStatus {
    /** The expected word was heard, with confidence at or above the threshold (or unknown). */
    Match,

    /** The expected word was heard, but the recognizer's confidence was below the threshold. */
    LowConfidence,

    /** A different word was heard in place of the expected one. */
    Substitution,

    /** The expected word wasn't heard. */
    Omission,

    /** A word was heard that the expected text doesn't contain. */
    Insertion,
}
