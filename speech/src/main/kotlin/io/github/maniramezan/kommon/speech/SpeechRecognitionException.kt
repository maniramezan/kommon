package io.github.maniramezan.kommon.speech

/**
 * A speech recognition run failed.
 *
 * @property reason What went wrong, in terms an app can act on.
 */
public class SpeechRecognitionException(
    public val reason: Reason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: "Speech recognition failed: $reason", cause) {
    /** Why recognition failed. */
    public enum class Reason {
        /** No recognition service is available on this device. */
        NotAvailable,

        /** The requested language isn't supported. */
        UnsupportedLocale,

        /** The language is supported but its model isn't installed yet. */
        ModelNotInstalled,

        /** The microphone permission is missing. */
        PermissionDenied,

        /** Nothing recognizable was heard. */
        NoMatch,

        /** A network problem prevented recognition. */
        Network,

        /** The recognizer is busy with another request. */
        Busy,

        /** Any other failure. */
        Other,
    }
}
