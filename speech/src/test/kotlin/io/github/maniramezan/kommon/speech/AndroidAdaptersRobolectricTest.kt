package io.github.maniramezan.kommon.speech

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAdaptersRobolectricTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `backend attaches the listener and builds the recognizer intent from the extras`() {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val backend = AndroidRecognizerBackend(recognizer, RecognizerKind.DefaultService)
        val listener = CountingListener()

        backend.start(
            listener,
            mapOf(
                RecognizerIntent.EXTRA_LANGUAGE to "en-US",
                RecognizerIntent.EXTRA_MAX_RESULTS to 3,
                RecognizerIntent.EXTRA_PARTIAL_RESULTS to true,
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS to 1200L,
            ),
        )

        val shadow = shadowOf(recognizer)
        shadowOf(Looper.getMainLooper()).idle()
        shadow.triggerOnReadyForSpeech(android.os.Bundle())
        assertEquals(1, listener.readyCount)
        val intent = assertNotNull(shadow.lastRecognizerIntent)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("en-US", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(3, intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(1200L, intent.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0L))
        backend.destroy()
    }

    @Test
    fun `the public session runs a recognition end to end through the framework`() =
        runTest(UnconfinedTestDispatcher()) {
            ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val session = AndroidSpeechRecognitionSession(context)
                session.recognize(SpeechRecognitionConfiguration(Locale.US)).test {
                    val active = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
                    shadowOf(Looper.getMainLooper()).idle()
                    active.triggerOnReadyForSpeech(android.os.Bundle())
                    assertEquals(SpeechRecognitionEvent.Ready(RecognizerKind.OnDevice), awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `requiring on-device without an on-device recognizer never creates the default recognizer`() {
        ShadowSpeechRecognizer.reset()
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(RecognitionService.SERVICE_INTERFACE),
            ResolveInfo(),
        )

        val factory = AndroidRecognizerBackendFactory(context)

        assertNull(factory.create(SpeechRecognitionConfiguration(Locale.US)))
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertFalse(isOnDeviceRecognitionAvailable(context))
    }

    @Test
    fun `not requiring on-device falls back to the default recognizer`() {
        ShadowSpeechRecognizer.reset()
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(RecognitionService.SERVICE_INTERFACE),
            ResolveInfo(),
        )

        val backend = AndroidRecognizerBackendFactory(context).create(SpeechRecognitionConfiguration(Locale.US, requiresOnDevice = false))

        assertEquals(RecognizerKind.DefaultService, backend?.kind)
        assertNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test
    fun `an available on-device recognizer is used for both settings`() {
        ShadowSpeechRecognizer.reset()
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)

        assertTrue(isOnDeviceRecognitionAvailable(context))
        val factory = AndroidRecognizerBackendFactory(context)
        assertEquals(RecognizerKind.OnDevice, factory.create(SpeechRecognitionConfiguration(Locale.US))?.kind)
        assertEquals(
            RecognizerKind.OnDevice,
            factory.create(SpeechRecognitionConfiguration(Locale.US, requiresOnDevice = false))?.kind,
        )
    }

    @Test
    @Config(sdk = [30])
    fun `on-device recognition is unavailable below Android 12`() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        assertFalse(isOnDeviceRecognitionAvailable(context))
    }

    @Test
    fun `public sessions construct without touching the engines`() {
        assertNotNull(AndroidSpeechRecognitionSession(context))
        AndroidSpeechSynthesisSession(context).close()
    }

    private class CountingListener : RecognitionListener {
        var readyCount = 0

        override fun onReadyForSpeech(params: android.os.Bundle?) {
            readyCount++
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = Unit

        override fun onResults(results: android.os.Bundle?) = Unit

        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit

        override fun onEvent(
            eventType: Int,
            params: android.os.Bundle?,
        ) = Unit
    }
}
