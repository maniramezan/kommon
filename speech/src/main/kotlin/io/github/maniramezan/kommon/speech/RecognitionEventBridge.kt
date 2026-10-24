package io.github.maniramezan.kommon.speech

import android.annotation.SuppressLint
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.SpeechRecognizer
import io.github.maniramezan.kommon.foundation.KommonLogger

/** Receives the events a [RecognitionEventBridge] translates, decoupled from any coroutine type. */
internal interface RecognitionSink {
    /** Delivers an [event] to the collector. */
    fun emit(event: SpeechRecognitionEvent)

    /** Completes the run successfully. */
    fun complete()

    /** Ends the run with [error]. */
    fun fail(error: SpeechRecognitionException)
}

/**
 * Translates `RecognitionListener` callbacks into [SpeechRecognitionEvent]s on a [RecognitionSink].
 *
 * Holds the per-run state: the accumulated transcript, the latest partial (used when the
 * recognizer ends with no match after something was heard) and whether the run has finished.
 * Logs only lengths, codes, and locales, never transcript text.
 */
internal class RecognitionEventBridge(
    private val configuration: SpeechRecognitionConfiguration,
    private val kind: RecognizerKind,
    private val sdkInt: Int,
    private val sink: RecognitionSink,
    private val logger: KommonLogger,
) : RecognitionListener {
    private val accumulator = TranscriptAccumulator()
    private var lastPartial: String = ""
    private var finished = false

    override fun onReadyForSpeech(params: Bundle?) {
        logger.debug(TAG, "ready locale=${configuration.locale.toLanguageTag()} recognizer=$kind")
        sink.emit(SpeechRecognitionEvent.Ready(kind))
    }

    override fun onBeginningOfSpeech() {
        logger.debug(TAG, "speech began")
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (finished) return
        sink.emit(SpeechRecognitionEvent.AudioLevel(AudioLevelMapper.level(rmsdB)))
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        if (finished) return
        logger.debug(TAG, "speech ended")
        sink.emit(SpeechRecognitionEvent.SilenceDetected)
    }

    override fun onEvent(
        eventType: Int,
        params: Bundle?,
    ) = Unit

    override fun onPartialResults(partialResults: Bundle?) {
        if (finished) return
        val text =
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
        if (text.isEmpty()) return
        lastPartial = text
        sink.emit(SpeechRecognitionEvent.Transcript(accumulator.applyVolatile(text)))
    }

    override fun onResults(results: Bundle?) {
        if (finished) return
        val utterance = utteranceFrom(results) ?: lastPartial.takeIf { it.isNotEmpty() }?.let { partialAsUtterance(it) }
        if (utterance == null) {
            fail(SpeechRecognitionException(SpeechRecognitionException.Reason.NoMatch, "No speech was recognized"))
            return
        }
        logger.info(
            TAG,
            "final result chars=${utterance.text.length} words=${utterance.words.size} alternatives=${utterance.alternatives.size}",
        )
        sink.emit(SpeechRecognitionEvent.Transcript(accumulator.applyFinal(utterance)))
        finished = true
        sink.complete()
    }

    override fun onError(error: Int) {
        if (finished) return
        val reason = RecognitionErrorMapper.reason(error)
        logger.warning(TAG, "recognition error code=$error reason=$reason")
        if (RecognitionErrorMapper.isNothingRecognized(error) && lastPartial.isNotEmpty()) {
            val utterance = partialAsUtterance(lastPartial)
            sink.emit(SpeechRecognitionEvent.Transcript(accumulator.applyFinal(utterance)))
            finished = true
            sink.complete()
            return
        }
        fail(SpeechRecognitionException(reason, "SpeechRecognizer error $error"))
    }

    private fun fail(error: SpeechRecognitionException) {
        finished = true
        sink.fail(error)
    }

    private fun partialAsUtterance(text: String): RecognizedUtterance =
        RecognitionResultMapper.utterance(listOf(text), null, null, false) ?: RecognizedUtterance(text, emptyList())

    private fun utteranceFrom(results: Bundle?): RecognizedUtterance? {
        if (results == null) return null
        return RecognitionResultMapper.utterance(
            results = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
            confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
            parts = recognitionParts(results),
            includeTiming = configuration.requestsWordDetails,
        )
    }

    // The sdkInt guard below is the API 34 check lint cannot see through the injected value.
    @SuppressLint("NewApi")
    private fun recognitionParts(results: Bundle): List<RecognitionPartData>? {
        if (!configuration.requestsWordDetails || sdkInt < RecognizerIntentExtras.WORD_DETAILS_API_LEVEL) return null
        return results
            .getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java)
            ?.map { RecognitionPartData(it.rawText, it.confidenceLevel, it.timestampMillis) }
    }

    private companion object {
        const val TAG = "KommonSpeech"
    }
}
