package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.analytics.AnalyticsEvent
import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ConfigValueType
import io.github.maniramezan.kommon.sync.SyncCursorRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSyncCursorStoreTest {
    @Test
    fun `save then get round-trips a record`() =
        runTest {
            val store = FakeSyncCursorStore()
            assertNull(store.get("resource"))
            store.save(SyncCursorRecord(resourceName = "resource", cursor = "abc"))
            assertEquals("abc", store.get("resource")?.cursor)
        }
}

class RecordingAnalyticsClientTest {
    @Test
    fun `records tracked events, identifies, resets, and flushes`() {
        val client = RecordingAnalyticsClient()
        client.track(AnalyticsEvent("event_a"))
        client.identify("user-1")
        client.reset()
        client.flush()

        assertEquals(listOf(AnalyticsEvent("event_a")), client.trackedEvents)
        assertEquals(listOf("user-1"), client.identifiedUserIds)
        assertTrue(client.wasReset)
        assertTrue(client.wasFlushed)
    }
}

class FakeRemoteConfigClientTest {
    private val key =
        ConfigKey(id = "flag", description = "d", valueType = ConfigValueType.BOOL, defaultValue = ConfigValue.Bool(false))

    @Test
    fun `returns the default value when not overridden`() {
        val client = FakeRemoteConfigClient()
        assertEquals(ConfigValue.Bool(false), client.value(key))
    }

    @Test
    fun `returns the overridden value when seeded`() =
        runTest {
            val client = FakeRemoteConfigClient(overrides = mapOf(key to ConfigValue.Bool(true)))
            assertEquals(ConfigValue.Bool(true), client.value(key))
            client.fetchAndActivate()
            assertEquals(1, client.fetchAndActivateCallCount)
            assertEquals(1, client.allValues().size)
        }
}
