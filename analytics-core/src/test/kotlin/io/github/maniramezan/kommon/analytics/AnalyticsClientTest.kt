package io.github.maniramezan.kommon.analytics

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class NoOpAnalyticsClientTest {
    @Test
    fun `every call is a no-op that never throws`() {
        val client = NoOpAnalyticsClient()
        client.initialize("token")
        client.identify("user-1")
        client.track(AnalyticsEvent("test_event"))
        client.setUserProperties(mapOf("plan" to "free"))
        client.increment("count", 1.0)
        client.trackScreen("home")
        client.reset()
        client.flush()
    }

    @Test
    fun `logs every call through the injected logger`() {
        val logger = mockk<KommonLogger>(relaxed = true)
        val client = NoOpAnalyticsClient(logger)

        client.initialize("token")
        client.identify("user-1")
        client.track(AnalyticsEvent("test_event"))
        client.setUserProperties(mapOf("plan" to "free"))
        client.increment("count", 1.0)
        client.trackScreen("home")
        client.reset()
        client.flush()

        verify(exactly = 8) { logger.debug(any(), any()) }
    }
}

class LoggingAnalyticsClientTest {
    @Test
    fun `logs every call through the injected logger`() {
        val logger = mockk<KommonLogger>(relaxed = true)
        val client = LoggingAnalyticsClient(logger)

        client.initialize("token", "https://example.com")
        client.identify("user-1")
        client.track(AnalyticsEvent("test_event"))
        client.setUserProperties(mapOf("plan" to "free"))
        client.increment("count", 1.0)
        client.trackScreen("home")
        client.reset()
        client.flush()

        verify(exactly = 8) { logger.info(any(), any()) }
    }
}

class CompositeAnalyticsClientTest {
    @Test
    fun `forwards every call to all configured clients`() {
        val first = mockk<AnalyticsClient>(relaxed = true)
        val second = mockk<AnalyticsClient>(relaxed = true)
        val composite = CompositeAnalyticsClient(listOf(first, second))
        val event = AnalyticsEvent("test_event")

        composite.track(event)
        composite.identify("user-1")
        composite.reset()

        verify { first.track(event) }
        verify { second.track(event) }
        verify { first.identify("user-1") }
        verify { second.identify("user-1") }
        verify { first.reset() }
        verify { second.reset() }
    }
}
