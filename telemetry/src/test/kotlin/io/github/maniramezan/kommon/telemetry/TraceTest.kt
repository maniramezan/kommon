package io.github.maniramezan.kommon.telemetry

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TraceTest {
    private val spans = mutableListOf<OpenTelemetrySpan>()
    private val client = OpenTelemetryClient { spans += it }

    @Test
    fun `successful block records an OK span and returns its value`() {
        val result = client.trace("load", SpanKind.CLIENT, mapOf("id" to 7)) { "done" }

        assertEquals("done", result)
        val span = spans.single()
        assertEquals("load", span.name)
        assertEquals(SpanKind.CLIENT, span.kind)
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(mapOf<String, Any>("id" to 7), span.attributes)
        assertTrue(span.durationMs >= 0)
    }

    @Test
    fun `failing block records an ERROR span with the error type and rethrows`() {
        assertFailsWith<IllegalStateException> { client.trace<Unit>("load") { throw IllegalStateException("boom") } }

        val span = spans.single()
        assertEquals(SpanStatus.ERROR, span.status)
        assertEquals("java.lang.IllegalStateException", span.attributes[ERROR_TYPE_ATTRIBUTE])
    }

    @Test
    fun `cancellation is recorded as UNSET rather than an error`() {
        assertFailsWith<java.util.concurrent.CancellationException> {
            client.trace<Unit>("load") { throw java.util.concurrent.CancellationException("stop") }
        }

        assertEquals(SpanStatus.UNSET, spans.single().status)
    }
}
