package io.github.maniramezan.kommon.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncEngineBatchingTest {
    private lateinit var api: TestApi
    private lateinit var adapter: TestResourceAdapter
    private lateinit var engine: SyncEngine
    private lateinit var telemetry: SyncTelemetrySink
    private val cursors = mutableMapOf<String, SyncCursorRecord>()
    private val requests = mutableListOf<SyncRequest<TestUpsert, TestDelete>>()

    @Before
    fun setup() {
        api = mockk()
        adapter = TestResourceAdapter(api)
        telemetry = mockk(relaxed = true)
        val cursorStore = mockk<SyncCursorStore>()
        coEvery { cursorStore.get(any()) } coAnswers { cursors[firstArg<String>()] }
        coEvery { cursorStore.save(any()) } coAnswers {
            val record = firstArg<SyncCursorRecord>()
            cursors[record.resourceName] = record
        }
        engine = SyncEngine(cursorStore, telemetry, clockMs = { 100L })
        coEvery { api.sync(capture(requests)) } coAnswers {
            val request = firstArg<SyncRequest<TestUpsert, TestDelete>>()
            testResponse(applied = acknowledgements(request), cursor = "cursor-${requests.size}")
        }
    }

    @Test
    fun `legacy sync pushes all pending rows regardless of pull limit`() =
        runTest {
            seedPending(5)

            engine.sync(adapter, limit = 2).getOrThrow()

            assertEquals(listOf(5), requests.map { it.upserts.size })
            assertEquals(2, requests.single().limit)
        }

    @Test
    fun `batch size bounds upserts and deletes together without changing pull limit`() =
        runTest {
            seedPending(5)
            adapter.seed(adapter.row("local-2")!!.copy(isDeleted = true, syncState = SyncState.PENDING_DELETE))
            adapter.seed(adapter.row("local-5")!!.copy(isDeleted = true, syncState = SyncState.PENDING_DELETE))

            engine.sync(adapter, limit = 7, pushBatchSize = 2).getOrThrow()

            assertEquals(listOf(2, 2, 1), requests.map { it.upserts.size + it.deletes.size })
            assertEquals(listOf("local-1", "local-3", "local-4"), requests.flatMap { it.upserts }.map { it.id })
            assertEquals(listOf("local-2", "local-5"), requests.flatMap { it.deletes }.map { it.id })
            assertEquals(listOf(7, 7, 7), requests.map { it.limit })
            assertEquals(listOf(null, "cursor-1", "cursor-2"), requests.map { it.since })
            assertTrue(adapter.all().all { it.syncState == SyncState.SYNCED })
            assertEquals(true, adapter.row("local-2")?.isDeleted)
            assertEquals(true, adapter.row("local-5")?.isDeleted)
            verify(exactly = 1) { telemetry.onSyncStarted(adapter.resourceName) }
            verify(exactly = 1) { telemetry.onSyncCompleted(adapter.resourceName, SyncResponse.MODE_DELTA, 5, 0, 0L) }
        }

    @Test
    fun `exact batch boundary does not send an extra empty request`() =
        runTest {
            seedPending(4)

            engine.sync(adapter, pushBatchSize = 2).getOrThrow()

            assertEquals(listOf(2, 2), requests.map { it.upserts.size })
            assertTrue(requests.all { it.limit == SyncEngine.DEFAULT_LIMIT })
        }

    @Test
    fun `empty pending list still pulls once`() =
        runTest {
            engine.sync(adapter, pushBatchSize = 2).getOrThrow()

            assertEquals(1, requests.size)
            assertTrue(requests.single().upserts.isEmpty())
            assertTrue(requests.single().deletes.isEmpty())
            assertEquals("cursor-1", cursors[adapter.resourceName]?.cursor)
        }

    @Test
    fun `response pages drain before next batch uses the final cursor`() =
        runTest {
            seedPending(2)
            coEvery { api.sync(capture(requests)) } returnsMany
                listOf(
                    testResponse(
                        applied = listOf(SyncAppliedRecord("local-1", status = "created")),
                        serverChanges = listOf(TestChange(10, "first", 1L)),
                        cursor = "page-1",
                        hasMore = true,
                    ),
                    testResponse(serverChanges = listOf(TestChange(11, "second", 2L)), cursor = "page-2"),
                    testResponse(applied = listOf(SyncAppliedRecord("local-2", status = "created")), cursor = "last"),
                )

            engine.sync(adapter, limit = 9, pushBatchSize = 1).getOrThrow()

            assertEquals(listOf(null, "page-1", "page-2"), requests.map { it.since })
            assertEquals(listOf(1, 0, 1), requests.map { it.upserts.size })
            assertEquals(listOf(9, 9, 9), requests.map { it.limit })
            assertEquals("first", adapter.row("server-10")?.value)
            assertEquals("second", adapter.row("server-11")?.value)
            assertEquals("last", cursors[adapter.resourceName]?.cursor)
            verify { telemetry.onSyncCompleted(adapter.resourceName, SyncResponse.MODE_DELTA, 2, 2, 0L) }
        }

    @Test
    fun `full snapshot reconciles every page before sending the next pending batch`() =
        runTest {
            adapter.seed(TestEntity("server-10", "old", serverId = 10, syncState = SyncState.SYNCED))
            adapter.seed(TestEntity("server-11", "old", serverId = 11, syncState = SyncState.SYNCED))
            adapter.seed(TestEntity("server-12", "gone", serverId = 12, syncState = SyncState.SYNCED))
            seedPending(2)
            coEvery { api.sync(capture(requests)) } coAnswers {
                when (requests.size) {
                    1 ->
                        testResponse(
                            mode = SyncResponse.MODE_FULL,
                            serverChanges = listOf(TestChange(10, "first", 1L)),
                            cursor = "snapshot-1",
                            hasMore = true,
                        )
                    2 -> {
                        assertTrue(cursors.isEmpty())
                        assertEquals(false, adapter.row("server-11")?.isDeleted)
                        testResponse(
                            mode = SyncResponse.MODE_FULL,
                            serverChanges = listOf(TestChange(11, "second", 2L)),
                            cursor = "snapshot-end",
                        )
                    }
                    else -> {
                        assertEquals("snapshot-end", cursors[adapter.resourceName]?.cursor)
                        assertEquals(true, adapter.row("server-12")?.isDeleted)
                        assertEquals(false, adapter.row("local-2")?.isDeleted)
                        testResponse(cursor = "last")
                    }
                }
            }

            engine.sync(adapter, pushBatchSize = 1).getOrThrow()

            assertEquals(listOf(null, "snapshot-1", "snapshot-end"), requests.map { it.since })
            assertEquals(false, adapter.row("server-10")?.isDeleted)
            assertEquals(false, adapter.row("server-11")?.isDeleted)
        }

    @Test
    fun `later batch failure preserves earlier acks and leaves remaining rows pending for retry`() =
        runTest {
            seedPending(3)
            coEvery { api.sync(capture(requests)) } returns
                testResponse(
                    applied = listOf(SyncAppliedRecord("local-1", status = "created")),
                    cursor = "committed",
                ) andThenThrows IllegalStateException("network dropped")

            assertTrue(engine.sync(adapter, pushBatchSize = 1).isFailure)

            assertEquals(2, requests.size)
            assertEquals(SyncState.SYNCED, adapter.row("local-1")?.syncState)
            assertEquals(SyncState.PENDING_CREATE, adapter.row("local-2")?.syncState)
            assertEquals(SyncState.PENDING_CREATE, adapter.row("local-3")?.syncState)
            assertEquals("committed", cursors[adapter.resourceName]?.cursor)
            verify(exactly = 1) { telemetry.onSyncFailed(adapter.resourceName, "IllegalStateException", "network dropped") }
            verify(exactly = 0) { telemetry.onSyncCompleted(any(), any(), any(), any(), any()) }

            requests.clear()
            coEvery { api.sync(capture(requests)) } coAnswers {
                testResponse(applied = acknowledgements(firstArg()), cursor = "retried")
            }

            engine.sync(adapter, pushBatchSize = 1).getOrThrow()

            assertEquals(listOf("local-2", "local-3"), requests.flatMap { it.upserts }.map { it.id })
            assertEquals("committed", requests.first().since)
        }

    @Test
    fun `failed full snapshot does not start next batch or persist its partial cursor`() =
        runTest {
            seedPending(2)
            coEvery { api.sync(capture(requests)) } returns
                testResponse(
                    mode = SyncResponse.MODE_FULL,
                    cursor = "partial-snapshot",
                    hasMore = true,
                ) andThenThrows IllegalStateException("snapshot interrupted")

            assertTrue(engine.sync(adapter, pushBatchSize = 1).isFailure)

            assertEquals(listOf(1, 0), requests.map { it.upserts.size })
            assertTrue(cursors.isEmpty())
            assertEquals(SyncState.PENDING_CREATE, adapter.row("local-2")?.syncState)
        }

    @Test
    fun `pagination guard stops later batches when cursor does not advance`() =
        runTest {
            seedPending(2)
            coEvery { api.sync(capture(requests)) } returns testResponse(cursor = "stuck", hasMore = true)

            assertTrue(engine.sync(adapter, pushBatchSize = 1).exceptionOrNull() is IllegalStateException)

            assertEquals(listOf(1, 0), requests.map { it.upserts.size })
            assertEquals(SyncState.PENDING_CREATE, adapter.row("local-2")?.syncState)
        }

    @Test
    fun `acknowledgements cannot mark unsent batches synced`() =
        runTest {
            seedPending(2)
            coEvery { api.sync(capture(requests)) } coAnswers {
                if (requests.size == 2) assertEquals(SyncState.PENDING_CREATE, adapter.row("local-2")?.syncState)
                testResponse(applied = listOf(SyncAppliedRecord("local-2", status = "created")), cursor = "cursor")
            }

            engine.sync(adapter, pushBatchSize = 1).getOrThrow()

            assertEquals(SyncState.PENDING_CREATE, adapter.row("local-1")?.syncState)
            assertEquals(SyncState.SYNCED, adapter.row("local-2")?.syncState)
        }

    @Test
    fun `mid-flight edits remain pending and newly created rows wait for the next pass`() =
        runTest {
            seedPending(2)
            coEvery { api.sync(capture(requests)) } coAnswers {
                if (requests.size == 1) {
                    adapter.seed(adapter.row("local-1")!!.copy(value = "edited", localUpdatedAt = 1L))
                    adapter.seed(adapter.row("local-2")!!.copy(value = "edited later batch", localUpdatedAt = 2L))
                    adapter.seed(TestEntity("new-row", "created during sync"))
                }
                testResponse(applied = acknowledgements(firstArg()), cursor = "cursor")
            }

            engine.sync(adapter, pushBatchSize = 1).getOrThrow()

            assertEquals(listOf("local-1", "local-2"), requests.flatMap { it.upserts }.map { it.id })
            assertTrue(adapter.all().all { it.syncState == SyncState.PENDING_CREATE })
            assertEquals("edited", adapter.row("local-1")?.value)
            assertEquals("edited later batch", adapter.row("local-2")?.value)
        }

    @Test
    fun `full resync on a later batch applies acks before purge and drains before continuing`() =
        runTest {
            seedPending(3)
            coEvery { api.sync(capture(requests)) } coAnswers {
                when (requests.size) {
                    1 -> testResponse(applied = acknowledgements(firstArg()), cursor = "first")
                    2 -> testResponse(applied = acknowledgements(firstArg()), fullResyncRequired = true)
                    3 -> {
                        assertEquals(null, adapter.row("local-1"))
                        assertEquals(null, adapter.row("local-2"))
                        assertEquals(SyncState.PENDING_CREATE, adapter.row("local-3")?.syncState)
                        testResponse(
                            mode = SyncResponse.MODE_FULL,
                            serverChanges = listOf(TestChange(10, "restored", 1L)),
                            cursor = "fresh",
                        )
                    }
                    else -> testResponse(applied = acknowledgements(firstArg()), cursor = "last")
                }
            }

            engine.sync(adapter, pushBatchSize = 1).getOrThrow()

            assertEquals(listOf(null, "first", null, "fresh"), requests.map { it.since })
            assertEquals(listOf(1, 1, 0, 1), requests.map { it.upserts.size })
            assertEquals("restored", adapter.row("server-10")?.value)
            verify(exactly = 1) { telemetry.onFullResync(adapter.resourceName) }
            verify { telemetry.onSyncCompleted(adapter.resourceName, SyncResponse.MODE_DELTA, 3, 1, 0L) }
        }

    @Test
    fun `cancellation during later batch propagates without failed telemetry`() =
        runTest {
            seedPending(3)
            coEvery { api.sync(capture(requests)) } returns
                testResponse(
                    applied = listOf(SyncAppliedRecord("local-1", status = "created")),
                    cursor = "committed",
                ) andThenThrows CancellationException("cancelled")

            assertFailsWith<CancellationException> { engine.sync(adapter, pushBatchSize = 1) }

            assertEquals(2, requests.size)
            assertEquals(SyncState.SYNCED, adapter.row("local-1")?.syncState)
            assertEquals("committed", cursors[adapter.resourceName]?.cursor)
            verify(exactly = 0) { telemetry.onSyncFailed(any(), any(), any()) }
            verify(exactly = 0) { telemetry.onSyncCompleted(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `syncAll batches each resource and isolates a later batch failure`() =
        runTest {
            seedPending(3)
            val healthyApi = mockk<TestApi>()
            val healthy = TestResourceAdapter(healthyApi, resourceName = "healthy")
            healthy.seed(TestEntity("healthy-1", "one"))
            healthy.seed(TestEntity("healthy-2", "two"))
            coEvery { api.sync(capture(requests)) } returns testResponse(cursor = "first") andThenThrows
                IllegalStateException("first resource failed")
            val healthyRequests = mutableListOf<SyncRequest<TestUpsert, TestDelete>>()
            coEvery { healthyApi.sync(capture(healthyRequests)) } coAnswers {
                testResponse(applied = acknowledgements(firstArg()), cursor = "healthy-cursor")
            }

            assertTrue(engine.syncAll(listOf(adapter, healthy), limit = 3, pushBatchSize = 1).isFailure)

            assertEquals(2, requests.size)
            assertEquals(listOf(1, 1), healthyRequests.map { it.upserts.size })
            assertEquals(listOf(3, 3), healthyRequests.map { it.limit })
            assertTrue(healthy.all().all { it.syncState == SyncState.SYNCED })
            assertEquals("healthy-cursor", cursors[healthy.resourceName]?.cursor)
        }

    @Test
    fun `non-positive push batch sizes and pull limits fail before any API request`() =
        runTest {
            listOf(0, -1).forEach { invalid ->
                assertFailsWith<IllegalArgumentException> { engine.sync(adapter, pushBatchSize = invalid) }
                assertFailsWith<IllegalArgumentException> { engine.syncAll(listOf(adapter), pushBatchSize = invalid) }
                assertFailsWith<IllegalArgumentException> { engine.sync(adapter, limit = invalid, pushBatchSize = 1) }
                assertFailsWith<IllegalArgumentException> { engine.syncAll(listOf(adapter), limit = invalid, pushBatchSize = 1) }
            }
            coVerify(exactly = 0) { api.sync(any()) }
        }

    private fun seedPending(count: Int) {
        repeat(count) { index -> adapter.seed(TestEntity("local-${index + 1}", "value-${index + 1}")) }
    }

    private fun acknowledgements(request: SyncRequest<TestUpsert, TestDelete>): List<SyncAppliedRecord> =
        (request.upserts.map { it.id } + request.deletes.map { it.id }).map { SyncAppliedRecord(it, status = "applied") }
}
