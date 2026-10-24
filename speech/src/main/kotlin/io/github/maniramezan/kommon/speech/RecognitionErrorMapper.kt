package io.github.maniramezan.kommon.speech

import android.speech.SpeechRecognizer

/** Maps `SpeechRecognizer.ERROR_*` codes to [SpeechRecognitionException.Reason]. */
internal object RecognitionErrorMapper {
    fun reason(errorCode: Int): SpeechRecognitionException.Reason =
        when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_SERVER,
            -> SpeechRecognitionException.Reason.Network
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH,
            -> SpeechRecognitionException.Reason.NoMatch
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            -> SpeechRecognitionException.Reason.Busy
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechRecognitionException.Reason.PermissionDenied
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> SpeechRecognitionException.Reason.UnsupportedLocale
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechRecognitionException.Reason.ModelNotInstalled
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
            -> SpeechRecognitionException.Reason.NotAvailable
            else -> SpeechRecognitionException.Reason.Other
        }

    /** Whether [errorCode] means the recognizer heard nothing it could transcribe. */
    fun isNothingRecognized(errorCode: Int): Boolean = reason(errorCode) == SpeechRecognitionException.Reason.NoMatch
}
