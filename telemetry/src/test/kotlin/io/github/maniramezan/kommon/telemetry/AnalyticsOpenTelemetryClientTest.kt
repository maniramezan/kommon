package io.github.maniramezan.kommon.telemetry

import io.github.maniramezan.kommon.analytics.AnalyticsClient
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class AnalyticsOpenTelemetryClientTest {
    @Test
    fun `bridges a span into an analytics event`() {
        val analyticsClient = mockk<AnalyticsClient>(relaxed = true)
        val client = AnalyticsOpenTelemetryClient(analyticsClient)
        val span =
            OpenTelemetrySpan(
                name = "fetch_words",
                kind = "client",
                status = "ok",
                durationMs = 120L,
                attributes = mapOf("endpoint" to "/words"),
            )
        val eventSlot = slot<io.github.maniramezan.kommon.analytics.AnalyticsEvent>()

        client.recordSpan(span)

        verify { analyticsClient.track(capture(eventSlot)) }
        val event = eventSlot.captured
        assertEquals("otel_span_recorded", event.name)
        assertEquals("fetch_words", event.properties["span_name"])
        assertEquals("client", event.properties["span_kind"])
        assertEquals("ok", event.properties["span_status"])
        assertEquals(120L, event.properties["duration_ms"])
        assertEquals("/words", event.properties["endpoint"])
    }
}

class NoOpOpenTelemetryClientTest {
    @Test
    fun `discards every recorded span`() {
        val client = NoOpOpenTelemetryClient()
        client.recordSpan(OpenTelemetrySpan(name = "x", kind = "client", status = "ok", durationMs = 1L))
    }
}
