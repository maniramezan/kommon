package io.github.maniramezan.kommon.speech

import android.speech.tts.UtteranceProgressListener

/** Forwards `UtteranceProgressListener` callbacks to a [SynthesisEventRouter]. */
internal class RouterProgressListener(
    private val router: SynthesisEventRouter,
) : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) {
        utteranceId?.let(router::onStart)
    }

    override fun onDone(utteranceId: String?) {
        utteranceId?.let(router::onDone)
    }

    @Deprecated("Superseded by onError(String?, Int)")
    override fun onError(utteranceId: String?) {
        utteranceId?.let { router.onError(it, null) }
    }

    override fun onError(
        utteranceId: String?,
        errorCode: Int,
    ) {
        utteranceId?.let { router.onError(it, errorCode) }
    }

    override fun onStop(
        utteranceId: String?,
        interrupted: Boolean,
    ) {
        utteranceId?.let(router::onStop)
    }

    override fun onRangeStart(
        utteranceId: String?,
        start: Int,
        end: Int,
        frame: Int,
    ) {
        utteranceId?.let { router.onRange(it, start, end) }
    }
}
