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
    fun `successful block with default kind and attributes`() {
        spans.clear()
        val result = client.trace("load_default") { "done" }

        assertEquals("done", result)
        val span = spans.single()
        assertEquals("load_default", span.name)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(emptyMap<String, Any>(), span.attributes)
        assertTrue(span.durationMs >= 0)
    }

    @Test
    fun `successful block with default attributes only`() {
        spans.clear()
        val result = client.trace("load_attr", SpanKind.SERVER) { "done" }

        assertEquals("done", result)
        val span = spans.single()
        assertEquals("load_attr", span.name)
        assertEquals(SpanKind.SERVER, span.kind)
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(emptyMap<String, Any>(), span.attributes)
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
    fun `failing block with default parameters records ERROR span`() {
        spans.clear()
        assertFailsWith<IllegalStateException> { client.trace<Unit>("load_fail_default") { throw IllegalStateException("boom") } }

        val span = spans.single()
        assertEquals("load_fail_default", span.name)
        assertEquals(SpanKind.INTERNAL, span.kind)
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

    @Test
    fun `cancellation with default parameters is recorded as UNSET`() {
        spans.clear()
        assertFailsWith<java.util.concurrent.CancellationException> {
            client.trace<Unit>("load_cancel_default") { throw java.util.concurrent.CancellationException("stop") }
        }

        val span = spans.single()
        assertEquals("load_cancel_default", span.name)
        assertEquals(SpanKind.INTERNAL, span.kind)
        assertEquals(SpanStatus.UNSET, span.status)
    }

    @Test
    fun `sink failure cannot change a successful result`() {
        val failing = OpenTelemetryClient { error("sink down") }
        assertEquals("done", failing.trace("load") { "done" })
    }

    @Test
    fun `sink failure cannot replace an operation failure or cancellation`() {
        val failing = OpenTelemetryClient { error("sink down") }
        for (original in listOf(IllegalArgumentException("operation"), java.util.concurrent.CancellationException("cancelled"))) {
            val actual = assertFailsWith<Exception> { failing.trace<Unit>("load") { throw original } }
            kotlin.test.assertSame(original, actual)
        }
    }

    @Test
    fun `trace allows a block to suspend and records its result`() =
        kotlinx.coroutines.runBlocking {
            val result =
                client.trace("suspending") {
                    kotlinx.coroutines.yield()
                    "done"
                }
            assertEquals("done", result)
            assertEquals(SpanStatus.OK, spans.single().status)
        }

    @Test
    fun `non-local return still records a span`() {
        fun load(): String {
            client.trace("return") { return "done" }
        }

        assertEquals("done", load())
        assertEquals(SpanStatus.OK, spans.single().status)
    }
}
