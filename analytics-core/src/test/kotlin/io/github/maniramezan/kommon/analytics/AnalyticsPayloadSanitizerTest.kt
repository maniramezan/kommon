package io.github.maniramezan.kommon.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsPayloadSanitizerTest {
    @Test
    fun `truncates long string values`() {
        val longValue = "x".repeat(200)
        val sanitized = AnalyticsPayloadSanitizer.sanitizeEventProperties(mapOf("key" to longValue))
        assertEquals(100, (sanitized["key"] as String).length)
    }

    @Test
    fun `passes through numeric and boolean values unchanged`() {
        val sanitized = AnalyticsPayloadSanitizer.sanitizeEventProperties(mapOf("count" to 5, "active" to true))
        assertEquals(5, sanitized["count"])
        assertEquals(true, sanitized["active"])
    }

    @Test
    fun `caps the number of event parameters`() {
        val properties = (1..30).associate { "key$it" to it }
        val sanitized = AnalyticsPayloadSanitizer.sanitizeEventProperties(properties)
        assertEquals(25, sanitized.size)
    }

    @Test
    fun `truncates user property values to 36 chars`() {
        val sanitized = AnalyticsPayloadSanitizer.sanitizeUserPropertyValue("x".repeat(100))
        assertEquals(36, sanitized.length)
    }
}
