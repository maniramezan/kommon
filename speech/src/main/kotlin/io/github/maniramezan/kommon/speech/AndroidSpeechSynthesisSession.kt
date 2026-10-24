package io.github.maniramezan.kommon.speech

import android.content.Context
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger

/**
 * A [SpeechSynthesizing] backed by Android's `TextToSpeech`.
 *
 * The engine is created lazily on first use and kept until [close]. Each [speak] interrupts
 * whatever is being spoken (the previous request's flow ends with
 * [SpeechSynthesisEvent.Cancelled]). Call [close] when finished to release the engine; a closed
 * session creates a fresh engine on the next use.
 *
 * The logger receives lengths, language tags, and error codes only, never the spoken text.
 *
 * @param context Any context; the application context is retained.
 * @param logger Receives diagnostic messages. Defaults to [NoOpLogger].
 */
public class AndroidSpeechSynthesisSession private constructor(
    private val runner: SpeechSynthesisRunner,
) : SpeechSynthesizing by runner,
    AutoCloseable by runner {
    /**
     * Creates a session. The engine itself is not created until the first [speak] or [voices].
     */
    public constructor(
        context: Context,
        logger: KommonLogger = NoOpLogger,
    ) : this(SpeechSynthesisRunner(AndroidTtsBackendFactory(context.applicationContext ?: context), logger))
}
