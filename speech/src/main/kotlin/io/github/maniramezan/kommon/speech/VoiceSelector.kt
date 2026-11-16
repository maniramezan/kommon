package io.github.maniramezan.kommon.speech

/** Picks the voice for a [SpeechSynthesisRequest], keeping network voices out unless allowed. */
internal object VoiceSelector {
    /** The result of [select]. */
    sealed interface Selection {
        /** Speak with [voice]. */
        data class Chosen(
            val voice: SpeechVoice,
        ) : Selection

        /** No acceptable voice exists; fail with [error]. */
        data class Unavailable(
            val error: SpeechSynthesisException,
        ) : Selection
    }

    /**
     * Chooses a voice from [voices].
     *
     * Unless [SpeechSynthesisRequest.allowsNetworkVoices], only voices that work offline are
     * candidates. An explicit [SpeechSynthesisRequest.voiceId] is honored when it is a candidate;
     * otherwise (unknown, or a rejected network voice) the best candidate for the language is used.
     */
    fun select(
        request: SpeechSynthesisRequest,
        voices: List<SpeechVoice>,
    ): Selection {
        val candidates = if (request.allowsNetworkVoices) voices else voices.filterNot { it.requiresNetwork }
        val explicit = request.voiceId?.let { id -> candidates.firstOrNull { it.id == id } }
        val chosen = explicit ?: SpeechVoice.best(request.languageTag, candidates, request.allowsNetworkVoices)
        if (chosen != null) return Selection.Chosen(chosen)
        val message =
            if (request.allowsNetworkVoices) {
                "No voice is available for ${request.languageTag}"
            } else {
                "No local voice is installed for ${request.languageTag}"
            }
        return Selection.Unavailable(SpeechSynthesisException(message))
    }

    /** The extra `TextToSpeech` utterance parameters for [request]. */
    fun utteranceParams(request: SpeechSynthesisRequest): Map<String, String> =
        if (request.allowsNetworkVoices) emptyMap() else mapOf(NETWORK_SYNTHESIS_KEY to "false")

    @Suppress("DEPRECATION")
    private const val NETWORK_SYNTHESIS_KEY = android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS
}
