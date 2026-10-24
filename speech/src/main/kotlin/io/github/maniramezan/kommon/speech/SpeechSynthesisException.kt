package io.github.maniramezan.kommon.speech

/**
 * Speech synthesis failed.
 *
 * @property errorCode The `TextToSpeech` error code when the engine reported one, else `null`.
 */
public class SpeechSynthesisException(
    message: String,
    public val errorCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
