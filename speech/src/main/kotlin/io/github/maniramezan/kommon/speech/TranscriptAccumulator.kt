package io.github.maniramezan.kommon.speech

/** Builds a [SpeechTranscript] from a recognizer's stream of volatile and final results. */
internal class TranscriptAccumulator {
    var transcript: SpeechTranscript = SpeechTranscript()
        private set

    /** Records a volatile (preview) result, replacing the previous preview. */
    fun applyVolatile(text: String): SpeechTranscript {
        transcript = transcript.copy(volatileText = text)
        return transcript
    }

    /** Records a final result and clears the preview it replaces. Blank utterances are dropped. */
    fun applyFinal(utterance: RecognizedUtterance): SpeechTranscript {
        val utterances =
            if (utterance.text.isBlank()) {
                transcript.finalizedUtterances
            } else {
                transcript.finalizedUtterances + utterance
            }
        transcript = SpeechTranscript(finalizedUtterances = utterances, volatileText = "")
        return transcript
    }
}
