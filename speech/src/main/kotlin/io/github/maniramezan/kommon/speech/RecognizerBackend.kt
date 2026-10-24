package io.github.maniramezan.kommon.speech

import android.speech.RecognitionListener

/**
 * The few `SpeechRecognizer` operations a recognition run needs, so the run logic can be tested
 * without the Android framework. The production implementation is [AndroidRecognizerBackend].
 */
internal interface RecognizerBackend {
    /** Whether this is a dedicated on-device recognizer rather than the system default. */
    val usesOnDeviceRecognizer: Boolean

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
    /** A backend for [configuration], or `null` when no recognition service is available. */
    fun create(configuration: SpeechRecognitionConfiguration): RecognizerBackend?
}
