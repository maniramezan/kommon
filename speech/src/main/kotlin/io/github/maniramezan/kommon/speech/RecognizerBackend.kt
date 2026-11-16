package io.github.maniramezan.kommon.speech

import android.speech.RecognitionListener

/**
 * The few `SpeechRecognizer` operations a recognition run needs, so the run logic can be tested
 * without the Android framework. The production implementation is [AndroidRecognizerBackend].
 */
internal interface RecognizerBackend {
    /** Which kind of recognizer this backend wraps. */
    val kind: RecognizerKind

    /** Attaches [listener] and starts listening with the given `RecognizerIntent` [extras]. */
    fun start(
        listener: RecognitionListener,
        extras: Map<String, Any>,
    )

    /** Stops listening and asks for the final result. */
    fun stopListening()

    /** Releases the recognizer. */
    fun destroy()
}

/** Creates [RecognizerBackend]s. */
internal fun interface RecognizerBackendFactory {
    /**
     * A backend for [configuration], or `null` when none is allowed or available. When
     * [SpeechRecognitionConfiguration.requiresOnDevice] is set, an implementation must never create
     * the system default recognizer.
     */
    fun create(configuration: SpeechRecognitionConfiguration): RecognizerBackend?
}
