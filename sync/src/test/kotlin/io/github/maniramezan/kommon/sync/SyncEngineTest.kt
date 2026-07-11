package io.github.maniramezan.kommon.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Behavior contract for [SyncEngine], ported from Novalingo's `UserSyncCoordinatorTest` onto a
 * generic [TestEntity]/[TestResourceAdapter] pair. Covers the invariants that must hold for
 * *any* resource, regardless of domain: pushing pending rows, the ack guard, full-snapshot
 * tombstone reconciliation, pagination drain, and `fullResyncRequired` handling.
 */
class SyncEngineTest {
    private lateinit var api: TestApi
    private lateinit var cursorStore: SyncCursorStore
    private lateinit var engine: SyncEngine

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        cursorStore = mockk(relaxed = true)
        engine = SyncEngine(cursorStore)
    }

    @Test
    fun `sync sends pending upsert and marks applied synced`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            adapter.seed(TestEntity(id = "local-1", value = "hello", syncState = SyncState.PENDING_CREATE))
            val requestSlot = slot<SyncRequest<TestUpsert, TestDelete>>()
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(capture(requestSlot)) } returns
                testResponse(
                    applied = listOf(SyncAppliedRecord(key = "local-1", id = 101, status = "created", updatedAt = 999L)),
                    cursor = "next",
                )

            engine.sync(adapter)

            assertEquals(1, requestSlot.captured.upserts.size)
            assertEquals(emptyList(), requestSlot.captured.deletes)
            val stored = requireNotNull(adapter.row("local-1"))
            assertEquals(101, stored.serverId)
            assertEquals(SyncState.SYNCED, stored.syncState)
            assertEquals(false, stored.isDeleted)
            coVerify { cursorStore.save(match { it.resourceName == adapter.resourceName && it.cursor == "next" }) }
        }

    @Test
    fun `blocked rows are marked blocked without touching other rows`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            adapter.seed(TestEntity(id = "local-1", value = "hello", syncState = SyncState.PENDING_CREATE))
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returns
                testResponse(
                    applied = listOf(SyncAppliedRecord(key = "local-1", status = "blocked", reason = "quota_exceeded")),
                    serverChanges = listOf(TestChange(id = 9, value = "server", updatedAt = 200L)),
                    cursor = "next",
                )

            engine.sync(adapter)

            assertEquals(SyncState.BLOCKED, adapter.row("local-1")?.syncState)
            assertEquals(SyncState.SYNCED, adapter.row("server-9")?.syncState)
        }

    @Test
    fun `ack guard leaves row pending when edited mid-flight`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            // Seed represents the state *after* the request was built: localUpdatedAt advanced
            // while the request was in flight (an edit raced the network round-trip).
            adapter.seed(
                TestEntity(id = "local-1", value = "hello", syncState = SyncState.PENDING_CREATE, localUpdatedAt = 456L),
            )
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } coAnswers {
                // Simulate the mid-flight edit happening after the request left but before the ack arrives.
                adapter.upsert(adapter.row("local-1")!!.copy(localUpdatedAt = 999L))
                testResponse(
                    applied = listOf(SyncAppliedRecord(key = "local-1", id = 101, status = "created", updatedAt = 999L)),
                    cursor = "next",
                )
            }

            engine.sync(adapter)

            // The ack must NOT demote the freshly-edited row to synced.
            assertEquals(SyncState.PENDING_CREATE, adapter.row("local-1")?.syncState)
        }

    @Test
    fun `full snapshot tombstones clean rows missing from snapshot`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            adapter.seed(TestEntity(id = "local-1", value = "gone", syncState = SyncState.SYNCED))
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returns testResponse(mode = SyncResponse.MODE_FULL, cursor = null)

            engine.sync(adapter)

            // Absent from the full snapshot -> tombstoned locally.
            val row = requireNotNull(adapter.row("local-1"))
            assertEquals(true, row.isDeleted)
        }

    @Test
    fun `delta pagination drains all pages`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returnsMany
                listOf(
                    testResponse(
                        serverChanges = listOf(TestChange(id = 10, value = "alpha", updatedAt = 1L)),
                        cursor = "page-1",
                        hasMore = true,
                    ),
                    testResponse(
                        serverChanges = listOf(TestChange(id = 11, value = "beta", updatedAt = 2L)),
                        cursor = "page-2",
                        hasMore = false,
                    ),
                )

            engine.sync(adapter)

            coVerify(exactly = 2) { api.sync(any()) }
            assertEquals("alpha", adapter.row("server-10")?.value)
            assertEquals("beta", adapter.row("server-11")?.value)
            coVerify { cursorStore.save(match { it.cursor == "page-2" }) }
        }

    @Test
    fun `full resync purges synced rows and re-pulls a snapshot`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            adapter.seed(TestEntity(id = "stale", value = "stale", syncState = SyncState.SYNCED))
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returnsMany
                listOf(
                    testResponse(cursor = null, fullResyncRequired = true),
                    testResponse(mode = SyncResponse.MODE_FULL, cursor = "fresh"),
                )

            engine.sync(adapter)

            // purgeSynced() removed the stale synced row before the re-pull.
            assertEquals(null, adapter.row("stale"))
            coVerify(exactly = 2) { api.sync(any()) }
            coVerify { cursorStore.save(match { it.cursor == "fresh" }) }
        }

    @Test
    fun `syncAll isolates one resource's failure from the others`() =
        runTest {
            val failing = TestResourceAdapter(api, resourceName = "failing")
            val healthy = TestResourceAdapter(api, resourceName = "healthy")
            coEvery { cursorStore.get("failing") } throws IllegalStateException("boom")
            coEvery { cursorStore.get("healthy") } returns null
            coEvery { api.sync(any()) } returns testResponse()

            val result = engine.syncAll(listOf(failing, healthy))

            assertEquals(true, result.isFailure)
            coVerify { api.sync(any()) } // healthy resource still ran
        }

    @Test
    fun `telemetry failures do not fail sync`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            val telemetry = mockk<SyncTelemetrySink>(relaxed = true)
            engine = SyncEngine(cursorStore, telemetry)
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returns testResponse()
            every { telemetry.onSyncStarted(adapter.resourceName) } throws IllegalStateException("telemetry unavailable")
            every { telemetry.onSyncCompleted(any(), any(), any(), any(), any()) } throws IllegalStateException("telemetry unavailable")

            val result = engine.sync(adapter)

            assertEquals(true, result.isSuccess)
            coVerify { api.sync(any()) }
        }
}
