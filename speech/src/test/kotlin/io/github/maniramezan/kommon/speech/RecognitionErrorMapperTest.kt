package io.github.maniramezan.kommon.speech

import android.speech.SpeechRecognizer
import io.github.maniramezan.kommon.speech.SpeechRecognitionException.Reason
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecognitionErrorMapperTest {
    @Test
    fun `every SpeechRecognizer error code maps to the documented reason`() {
        val expected =
            mapOf(
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT to Reason.Network,
                SpeechRecognizer.ERROR_NETWORK to Reason.Network,
                SpeechRecognizer.ERROR_AUDIO to Reason.Other,
                SpeechRecognizer.ERROR_SERVER to Reason.Network,
                SpeechRecognizer.ERROR_CLIENT to Reason.Other,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT to Reason.NoMatch,
                SpeechRecognizer.ERROR_NO_MATCH to Reason.NoMatch,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY to Reason.Busy,
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to Reason.PermissionDenied,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS to Reason.Busy,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED to Reason.NotAvailable,
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to Reason.UnsupportedLocale,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to Reason.ModelNotInstalled,
                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT to Reason.NotAvailable,
                SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS to Reason.Other,
            )
        expected.forEach { (code, reason) -> assertEquals(reason, RecognitionErrorMapper.reason(code), "code $code") }
    }

    @Test
    fun `unknown codes map to other`() {
        assertEquals(Reason.Other, RecognitionErrorMapper.reason(999))
    }

    @Test
    fun `only no match and speech timeout mean nothing was recognized`() {
        assertTrue(RecognitionErrorMapper.isNothingRecognized(SpeechRecognizer.ERROR_NO_MATCH))
        assertTrue(RecognitionErrorMapper.isNothingRecognized(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertFalse(RecognitionErrorMapper.isNothingRecognized(SpeechRecognizer.ERROR_NETWORK))
    }
}
