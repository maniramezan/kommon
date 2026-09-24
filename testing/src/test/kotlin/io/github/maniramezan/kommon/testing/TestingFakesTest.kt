package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.analytics.AnalyticsEvent
import io.github.maniramezan.kommon.authsession.AuthSessionHint
import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ConfigValueType
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry
import io.github.maniramezan.kommon.remoteconfig.ValueSource
import io.github.maniramezan.kommon.sync.SyncCursorRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

class RecordingAnalyticsClientCoverageTest {
    @Test
    fun `records screens, increments, user properties, and initialization`() {
        val client = RecordingAnalyticsClient()
        client.initialize("token")
        client.trackScreen("home", mapOf("source" to "push"))
        client.increment("sessions", 1.0)
        client.setUserProperties(mapOf("plan" to "free"))
        client.setUserProperties(mapOf("plan" to "pro"))
        client.track(AnalyticsEvent("a"))

        assertEquals("token", client.initializedToken)
        assertEquals(listOf(RecordingAnalyticsClient.ScreenView("home", mapOf("source" to "push"))), client.trackedScreens)
        assertEquals(listOf(RecordingAnalyticsClient.Increment("sessions", 1.0)), client.increments)
        assertEquals(mapOf<String, Any>("plan" to "pro"), client.userProperties)
        assertEquals(listOf("a"), client.trackedEventNames)
    }
}

class FakeRemoteConfigClientMutationTest {
    private val flag = ConfigKey.bool("flag", "d", false)
    private val limit = ConfigKey.int("limit", "d", 5)

    @Test
    fun `set and clear change the resolved value`() {
        val client = FakeRemoteConfigClient()
        client.set(flag, ConfigValue.Bool(true))
        assertEquals(ConfigValue.Bool(true), client.value(flag))
        client.clear(flag)
        assertEquals(ConfigValue.Bool(false), client.value(flag))
    }

    @Test
    fun `set rejects a value of the wrong type`() {
        assertFailsWith<IllegalArgumentException> { FakeRemoteConfigClient().set(flag, ConfigValue.Int(1)) }
    }

    @Test
    fun `allValues reports defaults for registered keys without overrides`() {
        val client = FakeRemoteConfigClient(mapOf(flag to ConfigValue.Bool(true)), keys = listOf(flag, limit))
        assertEquals(
            listOf(
                ResolvedConfigEntry(
                    flag,
                    ConfigValue.Bool(true),
                    ValueSource.OVERRIDE,
                ),
                ResolvedConfigEntry(
                    limit,
                    ConfigValue.Int(5),
                    ValueSource.DEFAULT,
                ),
            ),
            client.allValues(),
        )
    }
}

class RecordingLoggerTest {
    @Test
    fun `records every level in order`() {
        val logger = RecordingLogger()
        val boom = IllegalStateException("boom")
        logger.debug("t", "d")
        logger.info("t", "i")
        logger.warning("t", "w")
        logger.error("t", "e", boom)

        assertEquals(
            listOf(RecordingLogger.Level.DEBUG, RecordingLogger.Level.INFO, RecordingLogger.Level.WARNING, RecordingLogger.Level.ERROR),
            logger.entries.map { it.level },
        )
        assertEquals(boom, logger.entriesAt(RecordingLogger.Level.ERROR).single().error)
    }
}

class FakeAuthTest {
    @Test
    fun `sign-in updates current user and auth state`() =
        runTest {
            val repository = FakeAuthRepository()
            val user = repository.signInWithEmailAndPassword("a@b.co", "pw").getOrThrow()

            assertEquals(user, repository.currentUser)
            assertEquals(user, repository.authStateFlow().value)
            assertTrue(repository.isAuthenticated)
            assertFalse(repository.isAnonymous)

            repository.signOut()
            assertNull(repository.currentUser)
            assertEquals(1, repository.signOutCount)
        }

    @Test
    fun `nextFailure fails exactly one call`() =
        runTest {
            val repository = FakeAuthRepository()
            val failure = IllegalStateException("network")
            repository.nextFailure = failure

            assertEquals(failure, repository.signInAnonymously().exceptionOrNull())
            assertTrue(repository.signInAnonymously().isSuccess)
            assertTrue(repository.isAnonymous)
        }

    @Test
    fun `token provider and session store fakes record state`() =
        runTest {
            val tokens = FakeAuthTokenProvider(token = "t")
            assertEquals("t", tokens.idToken(forceRefresh = true))
            assertEquals(listOf(true), tokens.requests)

            val store = InMemoryAuthSessionStore()
            store.record(AuthSessionHint(hasAccount = true))
            assertEquals(true, store.read()?.hasAccount)
            store.clear()
            assertNull(store.read())
        }
}
