package io.github.maniramezan.kommon.speech

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecognizerSelectionTest {
    @Test
    fun `an available on-device recognizer is always preferred`() {
        assertEquals(RecognizerKind.OnDevice, RecognizerSelection.choose(true, true, true))
        assertEquals(RecognizerKind.OnDevice, RecognizerSelection.choose(false, true, true))
    }

    @Test
    fun `requiring on-device never falls back to the default service`() {
        assertNull(RecognizerSelection.choose(requiresOnDevice = true, onDeviceAvailable = false, defaultServiceAvailable = true))
    }

    @Test
    fun `without the requirement the default service is the fallback`() {
        assertEquals(
            RecognizerKind.DefaultService,
            RecognizerSelection.choose(requiresOnDevice = false, onDeviceAvailable = false, defaultServiceAvailable = true),
        )
        assertNull(RecognizerSelection.choose(requiresOnDevice = false, onDeviceAvailable = false, defaultServiceAvailable = false))
    }

    @Test
    fun `the unavailable failure names on-device recognition when it was required`() {
        val required = RecognizerSelection.unavailable(true)
        assertEquals(SpeechRecognitionException.Reason.NotAvailable, required.reason)
        assertEquals("On-device recognition is not available", required.message)
        assertEquals("Speech recognition is unavailable", RecognizerSelection.unavailable(false).message)
    }
}
