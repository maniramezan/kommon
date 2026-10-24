package io.github.maniramezan.kommon.speech

import io.github.maniramezan.kommon.foundation.KommonLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The synthesis logic behind [AndroidSpeechSynthesisSession]: lazy engine creation, utterance
 * routing, interruption semantics, and shutdown.
 *
 * @param backendFactory Creates the engine on first use.
 * @param logger Receives diagnostic messages (never the spoken text).
 */
internal class SpeechSynthesisRunner(
    private val backendFactory: TtsBackendFactory,
    private val logger: KommonLogger,
) : SpeechSynthesizing,
    AutoCloseable {
    private val router = SynthesisEventRouter()
    private val engineLock = Mutex()

    @Volatile
    private var backend: TtsBackend? = null

    @Volatile
    private var currentUtteranceId: String? = null

    override fun speak(request: SpeechSynthesisRequest): Flow<SpeechSynthesisEvent> =
        callbackFlow {
            val engine = ensureBackend()
            val utteranceId = UUID.randomUUID().toString()
            router.register(utteranceId, ChannelSink(this))
            val failure = engine.prepare(request)
            if (failure != null) {
                router.unregister(utteranceId)
                close(failure)
                awaitClose()
                return@callbackFlow
            }
            currentUtteranceId = utteranceId
            logger.info(TAG, "speak chars=${request.text.length} language=${request.languageTag}")
            val result = engine.speak(request.text, utteranceId)
            if (!engine.isSuccess(result)) {
                router.unregister(utteranceId)
                logger.error(TAG, "speak rejected code=$result")
                close(SpeechSynthesisException("TextToSpeech.speak failed", result))
            }
            awaitClose {
                val stillActive = router.unregister(utteranceId)
                if (stillActive && currentUtteranceId == utteranceId) {
                    engine.stop()
                    logger.debug(TAG, "speech cancelled by collector")
                }
            }
        }.buffer(Channel.UNLIMITED)

    override fun stop() {
        backend?.stop()
        logger.debug(TAG, "stop requested")
    }

    override suspend fun voices(): List<SpeechVoice> {
        val engine = ensureBackend()
        val voices =
            runCatching { engine.voices() }
                .onFailure { logger.error(TAG, "reading voices failed", it) }
                .getOrDefault(emptyList())
        return VoiceMapper.sorted(voices)
    }

    override fun close() {
        val engine = backend
        backend = null
        currentUtteranceId = null
        if (engine != null) {
            engine.stop()
            engine.shutdown()
            logger.info(TAG, "synthesizer shut down")
        }
    }

    private suspend fun ensureBackend(): TtsBackend =
        engineLock.withLock {
            backend ?: createBackend().also { backend = it }
        }

    private suspend fun createBackend(): TtsBackend {
        logger.info(TAG, "initializing TextToSpeech")
        return try {
            backendFactory.create(router).also { logger.info(TAG, "TextToSpeech ready") }
        } catch (error: SpeechSynthesisException) {
            logger.error(TAG, "TextToSpeech init failed code=${error.errorCode}", error)
            throw error
        }
    }

    private class ChannelSink(
        private val scope: ProducerScope<SpeechSynthesisEvent>,
    ) : SynthesisSink {
        override fun emit(event: SpeechSynthesisEvent) {
            scope.trySend(event)
        }

        override fun complete() {
            scope.close()
        }

        override fun fail(error: SpeechSynthesisException) {
            scope.close(error)
        }
    }

    private companion object {
        const val TAG = "KommonSpeech"
    }
}
