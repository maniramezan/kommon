package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.speech.SpeechRecognitionConfiguration
import io.github.maniramezan.kommon.speech.SpeechRecognitionEvent
import io.github.maniramezan.kommon.speech.SpeechRecognitionException
import io.github.maniramezan.kommon.speech.SpeechRecognizing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [SpeechRecognizing] for consumer tests.
 *
 * Each collected run emits [script] in order, then throws [error] if set, otherwise completes.
 * Runs and [stop] calls are recorded. Like the real session, a run that
 * [requiresOnDevice][SpeechRecognitionConfiguration.requiresOnDevice] fails immediately with
 * [SpeechRecognitionException.Reason.NotAvailable] while [isOnDeviceAvailable] is false.
 *
 * @property script The events every run emits (include `Ready(RecognizerKind...)` to simulate the
 *   recognizer-in-use report).
 * @property error An exception thrown after the script, or `null` to complete normally.
 */
public class FakeSpeechRecognizer(
    public var script: List<SpeechRecognitionEvent> = emptyList(),
    public var error: SpeechRecognitionException? = null,
) : SpeechRecognizing {
    /** Whether the simulated device has an on-device recognizer. */
    public var isOnDeviceAvailable: Boolean = true

    /** The configuration of every run started, in order. */
    public val configurations: MutableList<SpeechRecognitionConfiguration> = mutableListOf()

    /** How many times [stop] was called. */
    public var stopCount: Int = 0
        private set

    override fun recognize(configuration: SpeechRecognitionConfiguration): Flow<SpeechRecognitionEvent> =
        flow {
            configurations += configuration
            if (configuration.requiresOnDevice && !isOnDeviceAvailable) {
                throw SpeechRecognitionException(
                    SpeechRecognitionException.Reason.NotAvailable,
                    "On-device recognition is not available",
                )
            }
            script.forEach { emit(it) }
            error?.let { throw it }
        }

    override fun stop() {
        stopCount++
    }
}
