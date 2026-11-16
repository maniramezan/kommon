package io.github.maniramezan.kommon.sync

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The engine reads local rows through [SyncResourceAdapter.reloadAll] once per acknowledgement
 * set and [SyncResourceAdapter.findExistingAll] once per response page, so adapters that override
 * them issue one keyed query instead of one per row.
 */
class SyncEngineLookupBatchingTest {
    private lateinit var api: TestApi
    private lateinit var rows: TestResourceAdapter
    private lateinit var adapter: KeyedLookupAdapter
    private lateinit var engine: SyncEngine

    @Before
    fun setup() {
        api = mockk()
        rows = TestResourceAdapter(api)
        adapter = KeyedLookupAdapter(rows)
        engine = SyncEngine(InMemoryCursorStore())
    }

    @Test
    fun `acknowledgements are reloaded in one batched read`() =
        runTest {
            (1..3).forEach { rows.seed(TestEntity("local-$it", "value-$it")) }
            coEvery { api.sync(any()) } returns
                testResponse(applied = (1..3).map { SyncAppliedRecord("local-$it", id = it, status = "created") })

            engine.sync(adapter).getOrThrow()

            assertEquals(listOf(setOf("local-1", "local-2", "local-3")), adapter.reloadAllCalls)
            assertEquals(0, adapter.singleReloads)
            assertEquals(SyncState.SYNCED, rows.row("local-2")?.syncState)
        }

    @Test
    fun `each response page is looked up once with one change per key`() =
        runTest {
            rows.seed(TestEntity("local-7", "old", serverId = 7, syncState = SyncState.SYNCED))
            coEvery { api.sync(any()) } returnsMany
                listOf(
                    testResponse(
                        serverChanges =
                            listOf(
                                TestChange(id = 7, value = "first", updatedAt = 1L),
                                TestChange(id = 8, value = "new", updatedAt = 2L),
                                TestChange(id = 7, value = "second", updatedAt = 3L),
                            ),
                        cursor = "page-1",
                        hasMore = true,
                    ),
                    testResponse(serverChanges = listOf(TestChange(id = 9, value = "last", updatedAt = 4L)), cursor = "page-2"),
                )

            engine.sync(adapter).getOrThrow()

            assertEquals(listOf(listOf(7, 8), listOf(9)), adapter.findExistingAllCalls)
            assertEquals(0, adapter.singleFinds)
            assertEquals("second", rows.row("local-7")?.value)
            assertEquals("new", rows.row("server-8")?.value)
        }

    @Test
    fun `ack guard still skips rows missing from the batched reload`() =
        runTest {
            rows.seed(TestEntity("local-1", "kept"))
            rows.seed(TestEntity("local-2", "removed"))
            adapter.hiddenFromReload += "local-2"
            coEvery { api.sync(any()) } returns
                testResponse(applied = listOf("local-1", "local-2").map { SyncAppliedRecord(it, status = "created") })

            engine.sync(adapter).getOrThrow()

            assertEquals(SyncState.SYNCED, rows.row("local-1")?.syncState)
            assertEquals(SyncState.PENDING_CREATE, rows.row("local-2")?.syncState)
        }

    @Test
    fun `pages without server changes skip the lookup`() =
        runTest {
            coEvery { api.sync(any()) } returns testResponse(cursor = "empty")

            engine.sync(adapter).getOrThrow()

            assertEquals(emptyList(), adapter.findExistingAllCalls)
            assertNull(rows.row("server-1"))
        }
}

/** Overrides the batched lookups and counts how the engine reads rows. */
private class KeyedLookupAdapter(
    private val delegate: TestResourceAdapter,
) : SyncResourceAdapter<TestEntity, TestUpsert, TestDelete, TestChange> by delegate {
    val reloadAllCalls = mutableListOf<Set<String>>()
    val findExistingAllCalls = mutableListOf<List<Int>>()
    val hiddenFromReload = mutableSetOf<String>()
    var singleReloads = 0
    var singleFinds = 0

    override suspend fun reload(entity: TestEntity): TestEntity? {
        singleReloads++
        return delegate.reload(entity)
    }

    override suspend fun findExisting(change: TestChange): TestEntity? {
        singleFinds++
        return delegate.findExisting(change)
    }

    override suspend fun reloadAll(entities: List<TestEntity>): Map<String, TestEntity> {
        reloadAllCalls += entities.map { it.id }.toSet()
        return entities
            .filter { it.id !in hiddenFromReload }
            .mapNotNull { entity -> delegate.row(entity.id)?.let { entity.id to it } }
            .toMap()
    }

    override suspend fun findExistingAll(changes: List<TestChange>): Map<String, TestEntity> {
        findExistingAllCalls += changes.map { it.id }
        val wanted = changes.associateBy { it.id }
        return delegate
            .all()
            .filter { it.serverId in wanted }
            .associateBy { entity -> "server-${entity.serverId}" }
    }
}

private class InMemoryCursorStore : SyncCursorStore {
    private val records = mutableMapOf<String, SyncCursorRecord>()

    override suspend fun get(resourceName: String): SyncCursorRecord? = records[resourceName]

    override suspend fun save(record: SyncCursorRecord) {
        records[record.resourceName] = record
    }
}
