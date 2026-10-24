package io.github.maniramezan.kommon.speech

import app.cash.turbine.test
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.speech.SpeechVoice.Quality
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpeechSynthesisRunnerTest {
    private class FakeBackend : TtsBackend {
        var voicesResult: () -> List<SpeechVoice> = { emptyList() }
        var prepareFailure: SpeechSynthesisException? = null
        var speakResult = 0
        val spoken = mutableListOf<Pair<String, String>>()
        val prepared = mutableListOf<SpeechSynthesisRequest>()
        var stopCount = 0
        var shutdownCount = 0

        override fun voices(): List<SpeechVoice> = voicesResult()

        override fun prepare(request: SpeechSynthesisRequest): SpeechSynthesisException? {
            prepared += request
            return prepareFailure
        }

        override fun speak(
            text: String,
            utteranceId: String,
        ): Int {
            spoken += text to utteranceId
            return speakResult
        }

        override fun isSuccess(result: Int): Boolean = result == 0

        override fun stop() {
            stopCount++
        }

        override fun shutdown() {
            shutdownCount++
        }
    }

    private class RecordingLogger : KommonLogger {
        val messages = mutableListOf<String>()

        override fun debug(
            tag: String,
            message: String,
        ) {
            messages += message
        }

        override fun info(
            tag: String,
            message: String,
        ) {
            messages += message
        }

        override fun warning(
            tag: String,
            message: String,
            error: Throwable?,
        ) {
            messages += message
        }

        override fun error(
            tag: String,
            message: String,
            error: Throwable?,
        ) {
            messages += message
        }
    }

    private val backend = FakeBackend()
    private var routerSeen: SynthesisEventRouter? = null
    private var createCount = 0
    private val logger = RecordingLogger()
    private val runner =
        SpeechSynthesisRunner(
            backendFactory = {
                createCount++
                routerSeen = it
                backend
            },
            logger = logger,
        )
    private val request = SpeechSynthesisRequest("secret words", "en-US", rate = 0.8f)

    @Test
    fun `speaking routes engine progress to the flow and completes on done`() =
        runTest {
            runner.speak(request).test {
                val id = awaitUtteranceId()
                router().onStart(id)
                router().onRange(id, 0, 6)
                router().onDone(id)
                assertEquals(SpeechSynthesisEvent.Started, awaitItem())
                assertEquals(SpeechSynthesisEvent.WillSpeak(0, 6), awaitItem())
                assertEquals(SpeechSynthesisEvent.Finished, awaitItem())
                awaitComplete()
            }
            assertEquals(listOf(request), backend.prepared)
            assertEquals("secret words", backend.spoken.single().first)
            assertTrue(logger.messages.none { it.contains("secret") })
        }

    private fun router(): SynthesisEventRouter = checkNotNull(routerSeen)

    private fun awaitUtteranceId(): String {
        val id = backend.spoken.lastOrNull()?.second
        assertNotNull(id)
        return id
    }

    @Test
    fun `an interrupted utterance ends with cancelled`() =
        runTest {
            runner.speak(request).test {
                router().onStop(awaitUtteranceId())
                assertEquals(SpeechSynthesisEvent.Cancelled, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `an engine error is thrown from the flow`() =
        runTest {
            runner.speak(request).test {
                router().onError(awaitUtteranceId(), -8)
                assertEquals(-8, (awaitError() as SpeechSynthesisException).errorCode)
            }
        }

    @Test
    fun `a rejected speak call fails the flow`() =
        runTest {
            backend.speakResult = -1
            runner.speak(request).test {
                assertEquals(-1, (awaitError() as SpeechSynthesisException).errorCode)
            }
        }

    @Test
    fun `a failed preparation fails the flow without speaking`() =
        runTest {
            backend.prepareFailure = SpeechSynthesisException("Language is not available", -2)
            runner.speak(request).test {
                assertEquals("Language is not available", awaitError().message)
            }
            assertTrue(backend.spoken.isEmpty())
        }

    @Test
    fun `cancelling an active collection stops the engine`() =
        runTest {
            runner.speak(request).test {
                awaitUtteranceId()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, backend.stopCount)
        }

    @Test
    fun `cancelling a collection that already finished does not stop the engine`() =
        runTest {
            runner.speak(request).test {
                router().onDone(awaitUtteranceId())
                awaitItem()
                awaitComplete()
            }
            assertEquals(0, backend.stopCount)
        }

    @Test
    fun `cancelling an interrupted older request does not stop its replacement`() =
        runTest {
            runner.speak(request).test {
                val firstId = awaitUtteranceId()
                runner.speak(request.copy(text = "next")).test {
                    awaitUtteranceId()
                    cancelAndIgnoreRemainingEvents()
                }
                assertEquals(1, backend.stopCount)
                router().onStop(firstId)
                assertEquals(SpeechSynthesisEvent.Cancelled, awaitItem())
                awaitComplete()
            }
            assertEquals(1, backend.stopCount)
        }

    @Test
    fun `the engine is created once and reused`() =
        runTest {
            runner.speak(request).test { cancelAndIgnoreRemainingEvents() }
            runner.speak(request).test { cancelAndIgnoreRemainingEvents() }
            runner.voices()
            assertEquals(1, createCount)
        }

    @Test
    fun `initialization failure is thrown and retried on the next use`() =
        runTest {
            var attempts = 0
            val flaky =
                SpeechSynthesisRunner(
                    backendFactory = {
                        attempts++
                        if (attempts == 1) throw SpeechSynthesisException("init failed", -1)
                        backend
                    },
                    logger = logger,
                )
            assertFailsWith<SpeechSynthesisException> { flaky.voices() }
            flaky.voices()
            assertEquals(2, attempts)
        }

    @Test
    fun `voices are sorted and a failing engine yields none`() =
        runTest {
            backend.voicesResult = {
                listOf(
                    SpeechVoice("b", "b", "en-US", Quality.Standard, false),
                    SpeechVoice("a", "a", "en-US", Quality.Premium, false),
                )
            }
            assertEquals(listOf("a", "b"), runner.voices().map { it.id })
            backend.voicesResult = { error("engine died") }
            assertEquals(emptyList(), runner.voices())
        }

    @Test
    fun `stop forwards to the engine only once it exists`() =
        runTest {
            runner.stop()
            assertEquals(0, backend.stopCount)
            runner.voices()
            runner.stop()
            assertEquals(1, backend.stopCount)
        }

    @Test
    fun `close shuts the engine down and a later use creates a new one`() =
        runTest {
            runner.voices()
            runner.close()
            assertEquals(1, backend.shutdownCount)
            assertEquals(1, backend.stopCount)
            runner.close()
            assertEquals(1, backend.shutdownCount)
            runner.voices()
            assertEquals(2, createCount)
        }
}
