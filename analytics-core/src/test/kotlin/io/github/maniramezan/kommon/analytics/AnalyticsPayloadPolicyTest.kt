package io.github.maniramezan.kommon.analytics

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalyticsPayloadPolicyTest {
    private val policy =
        AnalyticsPayloadPolicy(
            eventParameterLimit = 2,
            stringValueLimit = 5,
            userPropertyValueLimit = 3,
            redactedKeys = setOf("Email"),
        )

    @Test
    fun `custom limits apply to events and user properties`() {
        val event = AnalyticsPayloadSanitizer.sanitizeEventProperties(mapOf("a" to "abcdefgh", "b" to 1, "c" to 2), policy)
        val user = AnalyticsPayloadSanitizer.sanitizeUserProperties(mapOf("plan" to "premium"), policy)

        assertEquals(linkedMapOf<String, Any>("a" to "abcde", "b" to 1), event)
        assertEquals(linkedMapOf("plan" to "pre"), user)
    }

    @Test
    fun `redacted keys match case-insensitively and keep the key`() {
        val event = AnalyticsPayloadSanitizer.sanitizeEventProperties(mapOf("email" to "a@b.c"), policy)
        val user = AnalyticsPayloadSanitizer.sanitizeUserProperties(mapOf("EMAIL" to "a@b.c"), policy)

        assertEquals("[redacted]", event["email"])
        assertEquals("[redacted]", user["EMAIL"])
    }

    @Test
    fun `the default overloads keep the previous limits`() {
        assertEquals(
            AnalyticsPayloadSanitizer.sanitizeEventProperties(mapOf("k" to "x".repeat(200)), AnalyticsPayloadPolicy.DEFAULT),
            AnalyticsPayloadSanitizer.sanitizeEventProperties(mapOf("k" to "x".repeat(200))),
        )
        assertEquals(36, AnalyticsPayloadSanitizer.sanitizeUserPropertyValue("x".repeat(50)).length)
    }

    @Test
    fun `non-positive limits are rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { AnalyticsPayloadPolicy(eventParameterLimit = 0) }
        assertFailsWith<IllegalArgumentException> { AnalyticsPayloadPolicy(stringValueLimit = 0) }
        assertFailsWith<IllegalArgumentException> { AnalyticsPayloadPolicy(userPropertyValueLimit = -1) }
    }

    @Test
    fun `sanitizing client sanitizes payloads and forwards everything else unchanged`() {
        val delegate = mockk<AnalyticsClient>(relaxed = true)
        val client = SanitizingAnalyticsClient(delegate, policy)
        val tracked = slot<AnalyticsEvent>()

        client.initialize("token", "https://example.test")
        client.identify("user-1")
        client.track(AnalyticsEvent("signup", mapOf("email" to "a@b.c", "source" to "onboarding")))
        client.trackScreen("home", mapOf("email" to "a@b.c"))
        client.setUserProperties(mapOf("plan" to "premium"))
        client.increment("sessions", 1.0)
        client.reset()
        client.flush()

        verify { delegate.initialize("token", "https://example.test") }
        verify { delegate.identify("user-1") }
        verify { delegate.track(capture(tracked)) }
        assertEquals("signup", tracked.captured.name)
        assertEquals(mapOf<String, Any>("email" to "[redacted]", "source" to "onboa"), tracked.captured.properties)
        verify { delegate.trackScreen("home", mapOf("email" to "[redacted]")) }
        verify { delegate.setUserProperties(mapOf("plan" to "pre")) }
        verify { delegate.increment("sessions", 1.0) }
        verify { delegate.reset() }
        verify { delegate.flush() }
    }
}
