package io.github.maniramezan.kommon.speech

import android.speech.RecognizerIntent

/** Computes the `RecognizerIntent` extras for a [SpeechRecognitionConfiguration]. */
internal object RecognizerIntentExtras {
    /** First API level supporting `EXTRA_REQUEST_WORD_TIMING` and `EXTRA_REQUEST_WORD_CONFIDENCE`. */
    const val WORD_DETAILS_API_LEVEL: Int = 34

    /**
     * The extras to put on the recognizer intent.
     *
     * @param configuration The run's settings.
     * @param sdkInt The device API level, used to gate Android 14 extras.
     * @param usesOnDeviceRecognizer Whether a dedicated on-device recognizer is used; if not and
     *   [SpeechRecognitionConfiguration.preferOnDevice] is set, offline recognition is requested.
     */
    fun from(
        configuration: SpeechRecognitionConfiguration,
        sdkInt: Int,
        usesOnDeviceRecognizer: Boolean,
    ): Map<String, Any> {
        val extras = linkedMapOf<String, Any>()
        extras[RecognizerIntent.EXTRA_LANGUAGE_MODEL] = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        extras[RecognizerIntent.EXTRA_LANGUAGE] = configuration.locale.toLanguageTag()
        extras[RecognizerIntent.EXTRA_PARTIAL_RESULTS] = true
        extras[RecognizerIntent.EXTRA_MAX_RESULTS] = if (configuration.reportsAlternatives) configuration.maxAlternatives else 1
        if (configuration.preferOnDevice && !usesOnDeviceRecognizer) {
            extras[RecognizerIntent.EXTRA_PREFER_OFFLINE] = true
        }
        configuration.completeSilenceMillis?.let {
            extras[RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS] = it
        }
        if (configuration.requestsWordDetails && sdkInt >= WORD_DETAILS_API_LEVEL) {
            extras[RecognizerIntent.EXTRA_REQUEST_WORD_TIMING] = true
            extras[RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE] = true
        }
        return extras
    }
}
