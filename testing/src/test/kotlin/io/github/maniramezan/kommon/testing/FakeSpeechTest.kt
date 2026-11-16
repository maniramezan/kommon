package io.github.maniramezan.kommon.testing

import app.cash.turbine.test
import io.github.maniramezan.kommon.speech.RecognizerKind
import io.github.maniramezan.kommon.speech.SpeechRecognitionConfiguration
import io.github.maniramezan.kommon.speech.SpeechRecognitionEvent
import io.github.maniramezan.kommon.speech.SpeechRecognitionException
import io.github.maniramezan.kommon.speech.SpeechSynthesisEvent
import io.github.maniramezan.kommon.speech.SpeechSynthesisException
import io.github.maniramezan.kommon.speech.SpeechSynthesisRequest
import io.github.maniramezan.kommon.speech.SpeechTranscript
import io.github.maniramezan.kommon.speech.SpeechVoice
import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeSpeechRecognizerTest {
    private val configuration = SpeechRecognitionConfiguration(Locale.US)

    @Test
    fun `emits the script then completes and records the configuration`() =
        runTest {
            val transcript = SpeechTranscript(volatileText = "hi")
            val recognizer =
                FakeSpeechRecognizer(
                    listOf(SpeechRecognitionEvent.Ready(RecognizerKind.OnDevice), SpeechRecognitionEvent.Transcript(transcript)),
                )
            recognizer.recognize(configuration).test {
                assertEquals(SpeechRecognitionEvent.Ready(RecognizerKind.OnDevice), awaitItem())
                assertEquals(SpeechRecognitionEvent.Transcript(transcript), awaitItem())
                awaitComplete()
            }
            assertEquals(listOf(configuration), recognizer.configurations)
        }

    @Test
    fun `throws the configured error after the script`() =
        runTest {
            val failure = SpeechRecognitionException(SpeechRecognitionException.Reason.NoMatch)
            val recognizer = FakeSpeechRecognizer(listOf(SpeechRecognitionEvent.Ready(RecognizerKind.OnDevice)), failure)
            recognizer.recognize(configuration).test {
                awaitItem()
                assertEquals(failure, awaitError())
            }
        }

    @Test
    fun `fails with not available when on-device is required but unavailable`() =
        runTest {
            val recognizer = FakeSpeechRecognizer(listOf(SpeechRecognitionEvent.Ready(RecognizerKind.OnDevice)))
            recognizer.isOnDeviceAvailable = false
            recognizer.recognize(configuration).test {
                val error = awaitError() as SpeechRecognitionException
                assertEquals(SpeechRecognitionException.Reason.NotAvailable, error.reason)
            }
            recognizer.recognize(configuration.copy(requiresOnDevice = false)).test {
                awaitItem()
                awaitComplete()
            }
        }

    @Test
    fun `counts stop calls`() {
        val recognizer = FakeSpeechRecognizer()
        recognizer.stop()
        recognizer.stop()
        assertEquals(2, recognizer.stopCount)
    }
}

class FakeSpeechSynthesizerTest {
    private val request = SpeechSynthesisRequest("hello", "en-US")

    @Test
    fun `records requests and emits started then finished by default`() =
        runTest {
            val synthesizer = FakeSpeechSynthesizer()
            synthesizer.speak(request).test {
                assertEquals(SpeechSynthesisEvent.Started, awaitItem())
                assertEquals(SpeechSynthesisEvent.Finished, awaitItem())
                awaitComplete()
            }
            assertEquals(listOf(request), synthesizer.spoken)
        }

    @Test
    fun `applies the local voice rules when voices are configured`() =
        runTest {
            val network = SpeechVoice("n", "N", "en-US", SpeechVoice.Quality.Premium, true)
            val synthesizer = FakeSpeechSynthesizer(voicesToReturn = listOf(network))
            synthesizer.speak(request).test {
                assertIs<SpeechSynthesisException>(awaitError())
            }
            synthesizer.speak(request.copy(allowsNetworkVoices = true)).test {
                awaitItem()
                awaitItem()
                awaitComplete()
            }
        }

    @Test
    fun `throws the configured error and returns the configured voices`() =
        runTest {
            val voice = SpeechVoice("v", "V", "en-US", SpeechVoice.Quality.Standard, false)
            val synthesizer =
                FakeSpeechSynthesizer(script = emptyList(), voicesToReturn = listOf(voice), error = SpeechSynthesisException("nope"))
            synthesizer.speak(request).test {
                assertIs<SpeechSynthesisException>(awaitError())
            }
            assertEquals(listOf(voice), synthesizer.voices())
            synthesizer.stop()
            assertEquals(1, synthesizer.stopCount)
        }
}
