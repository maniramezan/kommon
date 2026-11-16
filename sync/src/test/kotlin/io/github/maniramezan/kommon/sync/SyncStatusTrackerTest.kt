package io.github.maniramezan.kommon.sync

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncStatusTrackerTest {
    private lateinit var api: TestApi
    private lateinit var cursorStore: SyncCursorStore
    private lateinit var delegate: SyncTelemetrySink
    private lateinit var tracker: SyncStatusTracker
    private lateinit var engine: SyncEngine
    private var now = 1_000L

    @Before
    fun setup() {
        api = mockk()
        cursorStore = mockk(relaxed = true)
        delegate = mockk(relaxed = true)
        tracker = SyncStatusTracker(delegate) { now }
        engine = SyncEngine(cursorStore, tracker) { now }
        coEvery { cursorStore.get(any()) } returns null
    }

    @Test
    fun `resources are absent until their first pass`() {
        assertNull(tracker.status("tasks"))
        assertEquals(emptyMap(), tracker.statuses.value)
    }

    @Test
    fun `an in-flight pass reports syncing with its start time`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            coEvery { api.sync(any()) } answers {
                assertEquals(SyncResourceStatus.Syncing(1_000L), tracker.status(adapter.resourceName))
                testResponse(cursor = "next")
            }

            engine.sync(adapter).getOrThrow()
        }

    @Test
    fun `a successful pass records its counts and forwards to the delegate`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            adapter.seed(TestEntity("local-1", "value"))
            coEvery { api.sync(any()) } answers {
                now = 1_250L
                testResponse(
                    applied = listOf(SyncAppliedRecord("local-1", status = "created")),
                    serverChanges = listOf(TestChange(id = 2, value = "server", updatedAt = 1L)),
                )
            }

            engine.sync(adapter).getOrThrow()

            assertEquals(
                SyncResourceStatus.Succeeded(1_250L, SyncResponse.MODE_DELTA, appliedCount = 1, changeCount = 1, durationMs = 250L),
                tracker.status(adapter.resourceName),
            )
            verify { delegate.onSyncStarted(adapter.resourceName) }
            verify { delegate.onSyncCompleted(adapter.resourceName, SyncResponse.MODE_DELTA, 1, 1, 250L) }
        }

    @Test
    fun `a failed pass records the error type and message`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            coEvery { api.sync(any()) } throws IllegalStateException("server down")

            assertTrue(engine.sync(adapter).isFailure)

            assertEquals(
                SyncResourceStatus.Failed(1_000L, "IllegalStateException", "server down"),
                tracker.status(adapter.resourceName),
            )
            verify { delegate.onSyncFailed(adapter.resourceName, "IllegalStateException", "server down") }
        }

    @Test
    fun `a cancelled pass is recorded as cancelled, not failed or stuck syncing`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            coEvery { api.sync(any()) } throws CancellationException("cancelled")

            assertFailsWith<CancellationException> { engine.sync(adapter) }

            assertEquals(SyncResourceStatus.Cancelled(1_000L), tracker.status(adapter.resourceName))
            verify { delegate.onSyncCancelled(adapter.resourceName) }
            verify(exactly = 0) { delegate.onSyncFailed(any(), any(), any()) }
        }

    @Test
    fun `syncAll tracks each resource independently`() =
        runTest {
            val failingApi = mockk<TestApi>()
            val healthy = TestResourceAdapter(api, resourceName = "healthy")
            val failing = TestResourceAdapter(failingApi, resourceName = "failing")
            coEvery { api.sync(any()) } returns testResponse()
            coEvery { failingApi.sync(any()) } throws IllegalArgumentException("bad")

            engine.syncAll(listOf(failing, healthy))

            assertTrue(tracker.status("healthy") is SyncResourceStatus.Succeeded)
            assertTrue(tracker.status("failing") is SyncResourceStatus.Failed)
        }

    @Test
    fun `item and resync events are forwarded without changing status`() {
        tracker.onItemBlocked("tasks", SyncAppliedRecord.STATUS_BLOCKED, "quota")
        tracker.onFullResync("tasks")

        assertNull(tracker.status("tasks"))
        verify { delegate.onItemBlocked("tasks", SyncAppliedRecord.STATUS_BLOCKED, "quota") }
        verify { delegate.onFullResync("tasks") }
    }

    @Test
    fun `a throwing delegate still updates status and never fails the sync`() =
        runTest {
            val adapter = TestResourceAdapter(api)
            every { delegate.onSyncCompleted(any(), any(), any(), any(), any()) } throws IllegalStateException("sink")
            coEvery { api.sync(any()) } returns testResponse()

            assertTrue(engine.sync(adapter).isSuccess)
            assertTrue(tracker.status(adapter.resourceName) is SyncResourceStatus.Succeeded)
        }
}
