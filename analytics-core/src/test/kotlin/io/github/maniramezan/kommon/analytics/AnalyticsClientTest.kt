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

        composite.initialize("token", "https://example.com")
        composite.track(event)
        composite.identify("user-1")
        composite.setUserProperties(mapOf("plan" to "free"))
        composite.increment("count", 1.0)
        composite.trackScreen("home", mapOf("source" to "deeplink"))
        composite.reset()
        composite.flush()

        for (client in listOf(first, second)) {
            verify { client.initialize("token", "https://example.com") }
            verify { client.track(event) }
            verify { client.identify("user-1") }
            verify { client.setUserProperties(mapOf("plan" to "free")) }
            verify { client.increment("count", 1.0) }
            verify { client.trackScreen("home", mapOf("source" to "deeplink")) }
            verify { client.reset() }
            verify { client.flush() }
        }
    }

    @Test
    fun `one failing client does not stop the others and the failure is logged`() {
        val failing = mockk<AnalyticsClient>(relaxed = true)
        val healthy = mockk<AnalyticsClient>(relaxed = true)
        val logger = mockk<KommonLogger>(relaxed = true)
        val event = AnalyticsEvent("test_event")
        io.mockk.every { failing.track(any()) } throws IllegalStateException("sdk down")
        val composite = CompositeAnalyticsClient(listOf(failing, healthy), logger)

        composite.track(event)

        verify { healthy.track(event) }
        verify { logger.error(any(), match { it.endsWith(".track failed") }, any<IllegalStateException>()) }
    }

    @Test
    fun `vararg constructor forwards to every client`() {
        val first = mockk<AnalyticsClient>(relaxed = true)
        val second = mockk<AnalyticsClient>(relaxed = true)

        CompositeAnalyticsClient(first, second).flush()

        verify { first.flush() }
        verify { second.flush() }
    }

    @Test
    fun `logger failure does not prevent delivery to remaining clients`() {
        val failing = mockk<AnalyticsClient>(relaxed = true)
        val healthy = mockk<AnalyticsClient>(relaxed = true)
        val logger = mockk<KommonLogger>(relaxed = true)
        io.mockk.every { failing.flush() } throws IllegalStateException("sdk down")
        io.mockk.every { logger.error(any(), any(), any()) } throws IllegalStateException("logger down")

        CompositeAnalyticsClient(listOf(failing, healthy), logger).flush()

        verify { healthy.flush() }
    }

    @Test
    fun `client cancellation propagates`() {
        val failing = mockk<AnalyticsClient>(relaxed = true)
        val cancellation = java.util.concurrent.CancellationException("cancelled")
        io.mockk.every { failing.flush() } throws cancellation

        val actual =
            kotlin.test.assertFailsWith<java.util.concurrent.CancellationException> {
                CompositeAnalyticsClient(failing).flush()
            }

        kotlin.test.assertSame(cancellation, actual)
    }
}
