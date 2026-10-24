package io.github.maniramezan.kommon.speech

/**
 * One position in a [TranscriptAlignment]: an expected word, a heard word, or both.
 *
 * @property reference The expected word as written in the reference text; `null` for an
 *   [AlignedTokenStatus.Insertion].
 * @property heard The heard word as the recognizer wrote it; `null` for an
 *   [AlignedTokenStatus.Omission].
 * @property confidence The recognizer's confidence in [heard], when it reported one.
 * @property status How the expected and heard words compare.
 */
public data class AlignedToken(
    val reference: String?,
    val heard: String?,
    val confidence: Float?,
    val status: AlignedTokenStatus,
)
