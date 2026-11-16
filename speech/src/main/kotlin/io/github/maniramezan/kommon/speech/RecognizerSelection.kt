package io.github.maniramezan.kommon.speech

/** Decides which recognizer a run may use, so audio never reaches a cloud service unless allowed. */
internal object RecognizerSelection {
    /**
     * The recognizer to create, or `null` when none is allowed or available.
     *
     * @param requiresOnDevice Whether only an on-device recognizer is acceptable.
     * @param onDeviceAvailable Whether a dedicated on-device recognizer exists.
     * @param defaultServiceAvailable Whether the system default service exists.
     */
    fun choose(
        requiresOnDevice: Boolean,
        onDeviceAvailable: Boolean,
        defaultServiceAvailable: Boolean,
    ): RecognizerKind? =
        when {
            onDeviceAvailable -> RecognizerKind.OnDevice
            requiresOnDevice -> null
            defaultServiceAvailable -> RecognizerKind.DefaultService
            else -> null
        }

    /** The failure to report when [choose] returned `null`. */
    fun unavailable(requiresOnDevice: Boolean): SpeechRecognitionException =
        SpeechRecognitionException(
            SpeechRecognitionException.Reason.NotAvailable,
            if (requiresOnDevice) "On-device recognition is not available" else "Speech recognition is unavailable",
        )
}
