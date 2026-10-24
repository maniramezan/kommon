package io.github.maniramezan.kommon.speech

import android.speech.RecognizerIntent
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecognizerIntentExtrasTest {
    private val configuration = SpeechRecognitionConfiguration(locale = Locale.forLanguageTag("fr-CA"))

    @Test
    fun `baseline extras request free form partial results in the locale`() {
        val extras = RecognizerIntentExtras.from(configuration, sdkInt = 33, kind = RecognizerKind.OnDevice)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, extras[RecognizerIntent.EXTRA_LANGUAGE_MODEL])
        assertEquals("fr-CA", extras[RecognizerIntent.EXTRA_LANGUAGE])
        assertEquals(true, extras[RecognizerIntent.EXTRA_PARTIAL_RESULTS])
        assertEquals(3, extras[RecognizerIntent.EXTRA_MAX_RESULTS])
    }

    @Test
    fun `alternatives off requests a single result`() {
        val extras =
            RecognizerIntentExtras.from(configuration.copy(reportsAlternatives = false), 33, kind = RecognizerKind.OnDevice)
        assertEquals(1, extras[RecognizerIntent.EXTRA_MAX_RESULTS])
    }

    @Test
    fun `offline is requested only when falling back to the default service`() {
        val fallback = RecognizerIntentExtras.from(configuration.copy(requiresOnDevice = false), 33, kind = RecognizerKind.DefaultService)
        val onDevice = RecognizerIntentExtras.from(configuration, 33, kind = RecognizerKind.OnDevice)
        assertEquals(true, fallback[RecognizerIntent.EXTRA_PREFER_OFFLINE])
        assertFalse(RecognizerIntent.EXTRA_PREFER_OFFLINE in onDevice)
    }

    @Test
    fun `complete silence length is passed as a long when set`() {
        val without = RecognizerIntentExtras.from(configuration, 33, kind = RecognizerKind.OnDevice)
        val with = RecognizerIntentExtras.from(configuration.copy(completeSilenceMillis = 1500), 33, kind = RecognizerKind.OnDevice)
        assertFalse(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS in without)
        assertEquals(1500L, with[RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS])
    }

    @Test
    fun `word details are requested only on Android 14 and later`() {
        val old = RecognizerIntentExtras.from(configuration, 33, kind = RecognizerKind.OnDevice)
        val current = RecognizerIntentExtras.from(configuration, 34, kind = RecognizerKind.OnDevice)
        val disabled = RecognizerIntentExtras.from(configuration.copy(requestsWordDetails = false), 34, kind = RecognizerKind.OnDevice)
        assertFalse(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING in old)
        assertTrue(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING in current)
        assertTrue(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE in current)
        assertFalse(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING in disabled)
    }
}
