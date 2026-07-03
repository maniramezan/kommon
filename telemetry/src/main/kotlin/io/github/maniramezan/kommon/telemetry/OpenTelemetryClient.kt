package io.github.maniramezan.kommon.telemetry

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.github.maniramezan.kommon.analytics.AnalyticsEvent

/** Lightweight OpenTelemetry-shaped span, decoupled from any specific tracing SDK. */
public data class OpenTelemetrySpan(
    val name: String,
    val kind: String,
    val status: String,
    val durationMs: Long,
    val attributes: Map<String, Any> = emptyMap(),
)

/** Sink for [OpenTelemetrySpan]s. Bridge this to a real tracer, or to [AnalyticsClient]. */
public interface OpenTelemetryClient {
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
