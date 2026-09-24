package io.github.maniramezan.kommon.telemetry

import java.util.concurrent.CancellationException

/** Non-inline recording logic keeps span emission measurable by coverage tooling. */
@PublishedApi
internal class TraceRecording(
    private val client: OpenTelemetryClient,
    private val name: String,
    private val kind: String,
    private val attributes: Map<String, Any>,
) {
    private val startNanos = System.nanoTime()
    private var failure: Throwable? = null

    fun failed(error: Throwable) {
        failure = error
    }

    fun finish() {
        val error = failure
        val status =
            when (error) {
                null -> SpanStatus.OK
                is CancellationException -> SpanStatus.UNSET
                else -> SpanStatus.ERROR
            }
        val spanAttributes =
            if (status == SpanStatus.ERROR) {
                attributes + (ERROR_TYPE_ATTRIBUTE to (error!!::class.qualifiedName ?: "unknown"))
            } else {
                attributes
            }
        val durationMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI
        // Never replace the block's result, failure, or cancellation with a sink exception.
        runCatching { client.recordSpan(OpenTelemetrySpan(name, kind, status, durationMs, spanAttributes)) }
    }

    private companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
