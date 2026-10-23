package io.github.maniramezan.kommon.analytics.debug

import io.github.maniramezan.kommon.analytics.AnalyticsEvent
import io.github.maniramezan.kommon.foundation.KommonLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryAnalyticsDebugStoreTest {
    @Test
    fun `append adds an entry with an incrementing id`() {
        val store = InMemoryAnalyticsDebugStore()
        store.append(AnalyticsDebugEntry(timestampMs = 1L, action = AnalyticsDebugAction.TRACK, name = "a", properties = emptyMap()))
        store.append(AnalyticsDebugEntry(timestampMs = 2L, action = AnalyticsDebugAction.TRACK, name = "b", properties = emptyMap()))

        val entries = store.entries.value
        assertEquals(2, entries.size)
        assertEquals(1L, entries[0].id)
        assertEquals(2L, entries[1].id)
    }

    @Test
    fun `clear empties the store`() {
        val store = InMemoryAnalyticsDebugStore()
        store.append(AnalyticsDebugEntry(timestampMs = 1L, action = AnalyticsDebugAction.TRACK, name = "a", properties = emptyMap()))
        store.clear()
        assertEquals(emptyList(), store.entries.value)
    }

    @Test
    fun `append evicts the oldest entry when capacity is reached`() {
        val store = InMemoryAnalyticsDebugStore(capacity = 2)
        store.append(AnalyticsDebugEntry(timestampMs = 1L, action = AnalyticsDebugAction.TRACK, name = "a", properties = emptyMap()))
        store.append(AnalyticsDebugEntry(timestampMs = 2L, action = AnalyticsDebugAction.TRACK, name = "b", properties = emptyMap()))
        store.append(AnalyticsDebugEntry(timestampMs = 3L, action = AnalyticsDebugAction.TRACK, name = "c", properties = emptyMap()))

        assertEquals(listOf("b", "c"), store.entries.value.map { it.name })
    }

    @Test
    fun `zero capacity is rejected`() {
        assertFailsWith<IllegalArgumentException> { InMemoryAnalyticsDebugStore(capacity = 0) }
    }
}

class AnalyticsCaptureClientTest {
    @Test
    fun `track forwards to the delegate and appends a debug entry`() =
        runTest {
            val delegate = mockk<io.github.maniramezan.kommon.analytics.AnalyticsClient>(relaxed = true)
            val debugStore = InMemoryAnalyticsDebugStore()
            val client = AnalyticsCaptureClient(delegate, debugStore, clockMs = { 42L })
            val event = AnalyticsEvent("test_event", mapOf("k" to "v"))

            client.track(event)

            verify { delegate.track(event) }
            val entry = debugStore.entries.value.single()
            assertEquals(AnalyticsDebugAction.TRACK, entry.action)
            assertEquals("test_event", entry.name)
            assertEquals(42L, entry.timestampMs)
        }

    @Test
    fun `every call is captured into the debug store and forwarded to the delegate`() =
        runTest {
            val delegate = mockk<io.github.maniramezan.kommon.analytics.AnalyticsClient>(relaxed = true)
            val debugStore = InMemoryAnalyticsDebugStore()
            val client = AnalyticsCaptureClient(delegate, debugStore)

            client.initialize("token", "https://example.com")
            client.identify("user-1")
            client.setUserProperties(mapOf("plan" to "free"))
            client.increment("count", 1.0)
            client.trackScreen("home", mapOf("source" to "deeplink"))
            client.reset()
            client.flush()

            verify { delegate.initialize("token", "https://example.com") }
            verify { delegate.identify("user-1") }
            verify { delegate.setUserProperties(mapOf("plan" to "free")) }
            verify { delegate.increment("count", 1.0) }
            verify { delegate.trackScreen("home", mapOf("source" to "deeplink")) }
            verify { delegate.reset() }
            verify { delegate.flush() }

            val actions = debugStore.entries.value.map { it.action }
            assertEquals(
                listOf(
                    AnalyticsDebugAction.IDENTIFY,
                    AnalyticsDebugAction.SET_USER_PROPERTIES,
                    AnalyticsDebugAction.INCREMENT,
                    AnalyticsDebugAction.TRACK_SCREEN,
                    AnalyticsDebugAction.RESET,
                    AnalyticsDebugAction.FLUSH,
                ),
                actions,
            )
        }

    @Test
    fun `a failing debug store never stops the call reaching the delegate`() =
        runTest {
            val delegate = mockk<io.github.maniramezan.kommon.analytics.AnalyticsClient>(relaxed = true)
            val brokenStore = mockk<AnalyticsDebugStore>()
            every { brokenStore.append(any()) } throws IllegalStateException("store closed")
            val client = AnalyticsCaptureClient(delegate, brokenStore)
            val event = AnalyticsEvent("purchase")

            client.track(event)
            client.trackScreen("home")

            verify { delegate.track(event) }
            verify { delegate.trackScreen("home", emptyMap()) }
        }

    @Test
    fun `payload sanitizing and logging failures never stop the delegate`() {
        val delegate = mockk<io.github.maniramezan.kommon.analytics.AnalyticsClient>(relaxed = true)
        val debugStore = InMemoryAnalyticsDebugStore()
        val logger = mockk<KommonLogger>()
        every { logger.error(any(), any(), any()) } throws IllegalStateException("logger closed")
        val brokenValue = mockk<Any>()
        every { brokenValue.toString() } throws IllegalStateException("cannot format")
        val client = AnalyticsCaptureClient(delegate, debugStore, logger = logger)
        val event = AnalyticsEvent("purchase", mapOf("value" to brokenValue))

        client.track(event)
        client.setUserProperties(event.properties)

        verify { delegate.track(event) }
        verify { delegate.setUserProperties(event.properties) }
        verify(exactly = 2) { logger.error(any(), any(), any()) }
        assertEquals(emptyList(), debugStore.entries.value)
    }

    @Test
    fun `capture cancellation propagates without calling the delegate`() {
        val delegate = mockk<io.github.maniramezan.kommon.analytics.AnalyticsClient>(relaxed = true)
        val debugStore = mockk<AnalyticsDebugStore>()
        every { debugStore.append(any()) } throws CancellationException("cancelled")
        val client = AnalyticsCaptureClient(delegate, debugStore)
        val event = AnalyticsEvent("purchase")

        assertFailsWith<CancellationException> { client.track(event) }

        verify(exactly = 0) { delegate.track(any()) }
    }

    @Test
    fun `logging cancellation propagates without calling the delegate`() {
        val delegate = mockk<io.github.maniramezan.kommon.analytics.AnalyticsClient>(relaxed = true)
        val debugStore = mockk<AnalyticsDebugStore>()
        every { debugStore.append(any()) } throws IllegalStateException("store closed")
        val logger = mockk<KommonLogger>()
        every { logger.error(any(), any(), any()) } throws CancellationException("cancelled")
        val client = AnalyticsCaptureClient(delegate, debugStore, logger = logger)

        assertFailsWith<CancellationException> { client.flush() }

        verify(exactly = 0) { delegate.flush() }
    }
}
