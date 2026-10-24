package io.github.maniramezan.kommon.speech

import io.github.maniramezan.kommon.foundation.KommonLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * The recognition-run logic behind [AndroidSpeechRecognitionSession]: single-run enforcement,
 * backend lifecycle, and mapping listener callbacks onto a cold flow.
 *
 * @param backendFactory Creates the recognizer for each run.
 * @param sdkInt The device API level, used to gate Android 14 features.
 * @param dispatcher The dispatcher every run executes on; the main thread in production.
 * @param postToMain Runs a block on the main thread, used by [stop].
 * @param logger Receives diagnostic messages (never transcript text).
 */
internal class SpeechRecognitionRunner(
    private val backendFactory: RecognizerBackendFactory,
    private val sdkInt: Int,
    private val dispatcher: CoroutineDispatcher,
    private val postToMain: (() -> Unit) -> Unit,
    private val logger: KommonLogger,
) : SpeechRecognizing {
    @Volatile
    private var active: RecognizerBackend? = null

    override fun recognize(configuration: SpeechRecognitionConfiguration): Flow<SpeechRecognitionEvent> =
        callbackFlow {
            if (active != null) {
                close(SpeechRecognitionException(SpeechRecognitionException.Reason.Busy, "A recognition run is already active"))
                awaitClose()
                return@callbackFlow
            }
            val backend = runCatching { backendFactory.create(configuration) }.getOrNull()
            if (backend == null) {
                logger.error(TAG, "no recognizer available requiresOnDevice=${configuration.requiresOnDevice}")
                close(RecognizerSelection.unavailable(configuration.requiresOnDevice))
                awaitClose()
                return@callbackFlow
            }
            active = backend
            logger.info(TAG, "start locale=${configuration.locale.toLanguageTag()} recognizer=${backend.kind}")
            val bridge = RecognitionEventBridge(configuration, backend.kind, sdkInt, ChannelSink(this), logger)
            val extras = RecognizerIntentExtras.from(configuration, sdkInt, backend.kind)
            runCatching { backend.start(bridge, extras) }.onFailure {
                logger.error(TAG, "start failed", it)
                close(SpeechRecognitionException(SpeechRecognitionException.Reason.Other, "Could not start listening", it))
            }
            awaitClose {
                active = null
                runCatching { backend.destroy() }.onFailure { logger.warning(TAG, "destroy failed", it) }
                logger.debug(TAG, "recognizer destroyed")
            }
        }.buffer(Channel.UNLIMITED).flowOn(dispatcher)

    override fun stop() {
        postToMain {
            active?.let {
                logger.debug(TAG, "stop requested")
                it.stopListening()
            }
        }
    }

    private class ChannelSink(
        private val scope: ProducerScope<SpeechRecognitionEvent>,
    ) : RecognitionSink {
        override fun emit(event: SpeechRecognitionEvent) {
            scope.trySend(event)
        }

        override fun complete() {
            scope.close()
        }

        override fun fail(error: SpeechRecognitionException) {
            scope.close(error)
        }
    }

    private companion object {
        const val TAG = "KommonSpeech"
    }
}
