package io.github.maniramezan.kommon.speech

/**
 * The few `TextToSpeech` operations a synthesis session needs, so the session logic can be tested
 * without the Android framework. The production implementation is [AndroidTtsBackend].
 */
internal interface TtsBackend {
    /** Every voice the engine offers, unsorted. */
    fun voices(): List<SpeechVoice>

    /** Applies rate, pitch, and [voice] for [request]; returns a failure if it can't be satisfied. */
    fun prepare(
        request: SpeechSynthesisRequest,
        voice: SpeechVoice,
    ): SpeechSynthesisException?

    /** Queues [text] flushing anything pending, with extra utterance [params]; returns the result code. */
    fun speak(
        text: String,
        utteranceId: String,
        params: Map<String, String>,
    ): Int

    /** Whether [speak] returning [result] means success. */
    fun isSuccess(result: Int): Boolean

    /** Stops speaking. */
    fun stop()

    /** Releases the engine. */
    fun shutdown()
}

/** Creates an initialized [TtsBackend] that reports progress to a [SynthesisEventRouter]. */
internal fun interface TtsBackendFactory {
    /** @throws SpeechSynthesisException when the engine fails to initialize. */
    suspend fun create(router: SynthesisEventRouter): TtsBackend
}
