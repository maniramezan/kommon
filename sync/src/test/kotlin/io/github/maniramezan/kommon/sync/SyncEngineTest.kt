package io.github.maniramezan.kommon.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `full resync applies push acks from initial response`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            val telemetry = mockk<SyncTelemetrySink>(relaxed = true)
            engine = SyncEngine(cursorStore, telemetry)
            adapter.seed(TestEntity(id = "local-1", value = "hello", syncState = SyncState.PENDING_CREATE))
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returnsMany
                listOf(
                    testResponse(
                        applied = listOf(SyncAppliedRecord(key = "local-1", id = 101, status = "created", updatedAt = 999L)),
                        fullResyncRequired = true,
                        cursor = null,
                    ),
                    testResponse(
                        mode = SyncResponse.MODE_FULL,
                        serverChanges = listOf(TestChange(id = 101, value = "hello from server", updatedAt = 999L)),
                        cursor = "fresh",
                    ),
                )

            engine.sync(adapter)

            val stored = requireNotNull(adapter.row("server-101"))
            assertEquals(101, stored.serverId)
            assertEquals(SyncState.SYNCED, stored.syncState)
            assertEquals("hello from server", stored.value)
            coVerify(exactly = 2) { api.sync(any()) }
            verify { telemetry.onSyncCompleted(adapter.resourceName, any(), 1, 1, any()) }
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

    @Test
    fun `pagination guard fails instead of looping when the cursor does not advance`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returns testResponse(cursor = "stuck", hasMore = true)

            val result = engine.sync(adapter)

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            coVerify(exactly = 2) { api.sync(any()) }
        }

    @Test
    fun `pagination requests each page with the previous page's cursor`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            val requests = mutableListOf<SyncRequest<TestUpsert, TestDelete>>()
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(capture(requests)) } returnsMany
                listOf(
                    testResponse(cursor = "page-1", hasMore = true),
                    testResponse(cursor = "page-2", hasMore = true),
                    testResponse(cursor = "page-3", hasMore = false),
                )

            engine.sync(adapter)

            assertEquals(listOf(null, "page-1", "page-2"), requests.map { it.since })
        }

    @Test
    fun `completed telemetry reports first-page acks even after pagination`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            adapter.seed(TestEntity(id = "local-1", value = "hello", syncState = SyncState.PENDING_CREATE))
            val telemetry = mockk<SyncTelemetrySink>(relaxed = true)
            engine = SyncEngine(cursorStore, telemetry, clockMs = { 1_000L })
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returnsMany
                listOf(
                    testResponse(
                        applied = listOf(SyncAppliedRecord(key = "local-1", id = 1, status = "created")),
                        cursor = "page-1",
                        hasMore = true,
                    ),
                    testResponse(cursor = "page-2"),
                )

            engine.sync(adapter)

            verify { telemetry.onSyncCompleted(adapter.resourceName, SyncResponse.MODE_DELTA, 1, 0, 0L) }
        }

    @Test
    fun `lastSyncedAt comes from the injected clock`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            engine = SyncEngine(cursorStore, clockMs = { 42L })
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } returns testResponse(cursor = "c")

            engine.sync(adapter)

            coVerify { cursorStore.save(match { it.lastSyncedAt == 42L }) }
        }

    @Test
    fun `syncAll returns the first failure with later failures suppressed`() =
        runTest {
            val first = TestResourceAdapter(api, resourceName = "first")
            val second = TestResourceAdapter(api, resourceName = "second")
            coEvery { cursorStore.get("first") } throws IllegalStateException("first")
            coEvery { cursorStore.get("second") } throws IllegalArgumentException("second")

            val error = requireNotNull(engine.syncAll(listOf(first, second)).exceptionOrNull())

            assertEquals("first", error.message)
            assertEquals(listOf("second"), error.suppressed.map { it.message })
        }

    @Test
    fun `cancellation propagates and is not reported as a sync failure`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            val telemetry = mockk<SyncTelemetrySink>(relaxed = true)
            engine = SyncEngine(cursorStore, telemetry)
            coEvery { cursorStore.get(adapter.resourceName) } returns null
            coEvery { api.sync(any()) } throws CancellationException("cancelled")

            assertFailsWith<CancellationException> { engine.sync(adapter) }

            verify(exactly = 0) { telemetry.onSyncFailed(any(), any(), any()) }
        }

    @Test
    fun `non-positive limit is rejected`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { engine.sync(TestResourceAdapter(api), limit = 0) }
        }
}
