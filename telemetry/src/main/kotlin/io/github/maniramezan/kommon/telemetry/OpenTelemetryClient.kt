package io.github.maniramezan.kommon.telemetry

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.github.maniramezan.kommon.analytics.AnalyticsEvent

/**
 * Lightweight OpenTelemetry-shaped span, decoupled from any specific tracing SDK.
 *
 * [kind] and [status] are free-form strings so bridges can pass through vendor values, but prefer
 * the OpenTelemetry names in [SpanKind] and [SpanStatus].
 */
public data class OpenTelemetrySpan(
    val name: String,
    val kind: String,
    val status: String,
    val durationMs: Long,
    val attributes: Map<String, Any> = emptyMap(),
)

/** Sink for [OpenTelemetrySpan]s. Bridge this to a real tracer, or to [AnalyticsClient]. */
public fun interface OpenTelemetryClient {
    public fun recordSpan(span: OpenTelemetrySpan)
}

/** Discards every recorded span. */
public class NoOpOpenTelemetryClient : OpenTelemetryClient {
    override fun recordSpan(span: OpenTelemetrySpan): Unit = Unit
}

/** Bridges [OpenTelemetrySpan]s into an [AnalyticsClient] as a generic `otel_span_recorded` event. */
public class AnalyticsOpenTelemetryClient(
    private val analyticsClient: AnalyticsClient,
) : OpenTelemetryClient {
    override fun recordSpan(span: OpenTelemetrySpan) {
        analyticsClient.track(
            AnalyticsEvent(
                name = "otel_span_recorded",
                properties = span.toAnalyticsProperties(),
            ),
        )
    }

    private fun OpenTelemetrySpan.toAnalyticsProperties(): Map<String, Any> =
        buildMap {
            putAll(attributes)
            put("span_name", name)
            put("span_kind", kind)
            put("span_status", status)
            put("duration_ms", durationMs)
        }
}

/** OpenTelemetry span kinds, spelled as the spec's enum names. */
public object SpanKind {
    public const val INTERNAL: String = "INTERNAL"
    public const val CLIENT: String = "CLIENT"
    public const val SERVER: String = "SERVER"
    public const val PRODUCER: String = "PRODUCER"
    public const val CONSUMER: String = "CONSUMER"
}

/** OpenTelemetry span status codes, spelled as the spec's enum names. */
public object SpanStatus {
    public const val UNSET: String = "UNSET"
    public const val OK: String = "OK"
    public const val ERROR: String = "ERROR"
}

/** Attribute key [trace] sets to the failing exception's class name, per OTel semantic conventions. */
public const val ERROR_TYPE_ATTRIBUTE: String = "error.type"

/**
 * Times [block] and records it as a span: [SpanStatus.OK] on success, [SpanStatus.ERROR] (with
 * [ERROR_TYPE_ATTRIBUTE]) when it throws. Cancellation is recorded as [SpanStatus.UNSET], since a
 * cancelled operation did not fail. The exception is always rethrown.
 *
 * ```kotlin
 * val lessons = telemetry.trace("load_lessons", SpanKind.CLIENT, mapOf("course" to courseId)) {
 *     api.fetchLessons(courseId)
 * }
 * ```
 */
@Suppress("TooGenericExceptionCaught") // Record every failure, then rethrow it unchanged.
public inline fun <T> OpenTelemetryClient.trace(
    name: String,
    kind: String = SpanKind.INTERNAL,
    attributes: Map<String, Any> = emptyMap(),
    block: () -> T,
): T {
    val startNanos = System.nanoTime()
    val result =
        try {
            block()
        } catch (cancellation: java.util.concurrent.CancellationException) {
            recordTimedSpan(name, kind, SpanStatus.UNSET, startNanos, attributes)
            throw cancellation
        } catch (error: Throwable) {
            val errorType = error::class.qualifiedName ?: "unknown"
            recordTimedSpan(name, kind, SpanStatus.ERROR, startNanos, attributes + (ERROR_TYPE_ATTRIBUTE to errorType))
            throw error
        }
    recordTimedSpan(name, kind, SpanStatus.OK, startNanos, attributes)
    return result
}

@PublishedApi
internal fun OpenTelemetryClient.recordTimedSpan(
    name: String,
    kind: String,
    status: String,
    startNanos: Long,
    attributes: Map<String, Any>,
) {
    val durationMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI
    recordSpan(OpenTelemetrySpan(name, kind, status, durationMs, attributes))
}

private const val NANOS_PER_MILLI: Long = 1_000_000L
