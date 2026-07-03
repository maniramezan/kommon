package io.github.maniramezan.kommon.analytics.debug

import io.github.maniramezan.kommon.analytics.AnalyticsEvent
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

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
}
