package io.github.maniramezan.kommon.speech

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import app.cash.turbine.test
import io.github.maniramezan.kommon.foundation.NoOpLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechRecognitionRunnerTest {
    private class FakeBackend(
        override val usesOnDeviceRecognizer: Boolean = false,
        private val failOnStart: Boolean = false,
    ) : RecognizerBackend {
        private var attached: RecognitionListener? = null

        fun listener(): RecognitionListener = checkNotNull(attached)

        var extras: Map<String, Any>? = null
        var stopCount = 0
        var destroyCount = 0

        override fun start(
            listener: RecognitionListener,
            extras: Map<String, Any>,
        ) {
            if (failOnStart) error("boom")
            attached = listener
            this.extras = extras
        }

        override fun stopListening() {
            stopCount++
        }

        override fun destroy() {
            destroyCount++
        }
    }

    private val configuration = SpeechRecognitionConfiguration(Locale.US)

    private fun results(text: String): Bundle {
        val bundle = mockk<Bundle>()
        every { bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf(text)
        every { bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) } returns null
        return bundle
    }

    private fun TestScope.runner(
        backend: RecognizerBackend?,
        posted: MutableList<() -> Unit> = mutableListOf(),
    ) = SpeechRecognitionRunner(
        backendFactory = { backend },
        sdkInt = 33,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        postToMain = { block -> posted += block },
        logger = NoOpLogger,
    )

    @Test
    fun `a full run emits events then completes and destroys the recognizer`() =
        runTest {
            val backend = FakeBackend(usesOnDeviceRecognizer = true)
            runner(backend).recognize(configuration).test {
                val listener = backend.listener()
                listener.onReadyForSpeech(null)
                assertEquals(SpeechRecognitionEvent.Ready, awaitItem())
                listener.onResults(results("hello"))
                assertEquals("hello", assertIs<SpeechRecognitionEvent.Transcript>(awaitItem()).transcript.text)
                awaitComplete()
            }
            assertEquals(1, backend.destroyCount)
            assertEquals("en-US", backend.extras?.get(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
        }

    @Test
    fun `a recognizer error is thrown from the flow`() =
        runTest {
            val backend = FakeBackend()
            runner(backend).recognize(configuration).test {
                backend.listener().onError(SpeechRecognizer.ERROR_NETWORK)
                val error = awaitError()
                assertEquals(SpeechRecognitionException.Reason.Network, (error as SpeechRecognitionException).reason)
            }
            assertEquals(1, backend.destroyCount)
        }

    @Test
    fun `cancelling collection destroys the recognizer`() =
        runTest {
            val backend = FakeBackend()
            runner(backend).recognize(configuration).test {
                backend.listener().onReadyForSpeech(null)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, backend.destroyCount)
        }

    @Test
    fun `a missing recognition service fails with not available`() =
        runTest {
            runner(null).recognize(configuration).test {
                val error = awaitError() as SpeechRecognitionException
                assertEquals(SpeechRecognitionException.Reason.NotAvailable, error.reason)
            }
        }

    @Test
    fun `a factory that throws fails with not available`() =
        runTest {
            val throwing =
                SpeechRecognitionRunner(
                    backendFactory = { error("no service") },
                    sdkInt = 33,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    postToMain = { it() },
                    logger = NoOpLogger,
                )
            throwing.recognize(configuration).test {
                assertEquals(
                    SpeechRecognitionException.Reason.NotAvailable,
                    (awaitError() as SpeechRecognitionException).reason,
                )
            }
        }

    @Test
    fun `a start failure fails with other and still destroys the recognizer`() =
        runTest {
            val backend = FakeBackend(failOnStart = true)
            runner(backend).recognize(configuration).test {
                val error = awaitError() as SpeechRecognitionException
                assertEquals(SpeechRecognitionException.Reason.Other, error.reason)
                assertTrue(error.cause is IllegalStateException)
            }
            assertEquals(1, backend.destroyCount)
        }

    @Test
    fun `a second concurrent run fails with busy and leaves the first running`() =
        runTest {
            val backend = FakeBackend()
            val runner = runner(backend)
            runner.recognize(configuration).test {
                runner.recognize(configuration).test {
                    val error = awaitError() as SpeechRecognitionException
                    assertEquals(SpeechRecognitionException.Reason.Busy, error.reason)
                }
                assertEquals(0, backend.destroyCount)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, backend.destroyCount)
        }

    @Test
    fun `a new run can start after the previous one finished`() =
        runTest {
            val first = FakeBackend()
            val second = FakeBackend()
            val backends = ArrayDeque(listOf(first, second))
            val runner =
                SpeechRecognitionRunner(
                    backendFactory = { backends.removeFirst() },
                    sdkInt = 33,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    postToMain = { it() },
                    logger = NoOpLogger,
                )
            runner.recognize(configuration).test {
                first.listener().onResults(results("one"))
                awaitItem()
                awaitComplete()
            }
            runner.recognize(configuration).test {
                second.listener().onResults(results("two"))
                assertEquals("two", assertIs<SpeechRecognitionEvent.Transcript>(awaitItem()).transcript.text)
                awaitComplete()
            }
        }

    @Test
    fun `stop asks the active recognizer to stop listening on the main thread`() =
        runTest {
            val backend = FakeBackend()
            val posted = mutableListOf<() -> Unit>()
            val runner = runner(backend, posted)
            runner.stop()
            posted.removeLast()()
            assertEquals(0, backend.stopCount)

            runner.recognize(configuration).test {
                runner.stop()
                assertEquals(0, backend.stopCount)
                posted.removeLast()()
                assertEquals(1, backend.stopCount)
                cancelAndIgnoreRemainingEvents()
            }
            runner.stop()
            posted.removeLast()()
            assertEquals(1, backend.stopCount)
        }
}
