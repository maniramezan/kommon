package io.github.maniramezan.kommon.sync

/** Minimal in-memory [SyncableEntity] used by [SyncEngineTest]. */
internal data class TestEntity(
    val id: String,
    val value: String,
    override val serverId: Int? = null,
    override val updatedAt: Long? = null,
    override val isDeleted: Boolean = false,
    override val syncState: String = SyncState.PENDING_CREATE,
    override val localUpdatedAt: Long = 0L,
) : SyncableEntity<TestEntity> {
    override fun withSync(
        serverId: Int?,
        updatedAt: Long?,
        isDeleted: Boolean,
        syncState: String,
    ): TestEntity = copy(serverId = serverId, updatedAt = updatedAt, isDeleted = isDeleted, syncState = syncState)
}

internal data class TestUpsert(
    val id: String,
    val value: String,
)

internal data class TestDelete(
    val id: String,
)

internal data class TestChange(
    val id: Int,
    val value: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Boundary the tests mock, standing in for a real HTTP API client. */
internal interface TestApi {
    suspend fun sync(request: SyncRequest<TestUpsert, TestDelete>): SyncResponse<TestChange>
}

/** In-memory [SyncResourceAdapter] test double; only [api] is a mock boundary. */
internal class TestResourceAdapter(
    private val api: TestApi,
    private val rows: MutableMap<String, TestEntity> = mutableMapOf(),
    override val resourceName: String = "test_resource",
) : SyncResourceAdapter<TestEntity, TestUpsert, TestDelete, TestChange> {
    fun seed(entity: TestEntity) {
        rows[entity.id] = entity
    }

    fun row(id: String): TestEntity? = rows[id]

    fun all(): List<TestEntity> = rows.values.toList()

    override suspend fun pending(): List<TestEntity> = rows.values.filter { it.syncState != SyncState.SYNCED }

    override fun syncKey(entity: TestEntity): String = entity.id

    override fun toUpsert(entity: TestEntity): TestUpsert = TestUpsert(entity.id, entity.value)

    override fun toDelete(entity: TestEntity): TestDelete = TestDelete(entity.id)

    override suspend fun api(request: SyncRequest<TestUpsert, TestDelete>): SyncResponse<TestChange> = api.sync(request)

    override suspend fun reload(entity: TestEntity): TestEntity? = rows[entity.id]

    override suspend fun upsert(entity: TestEntity) {
        rows[entity.id] = entity
    }

    override suspend fun findExisting(change: TestChange): TestEntity? = rows.values.find { it.serverId == change.id }

    override fun changeKey(change: TestChange): String = "server-${change.id}"

    override fun isChangeDeleted(change: TestChange): Boolean = change.deleted

    override fun toEntity(
        change: TestChange,
        existing: TestEntity?,
    ): TestEntity =
        TestEntity(
            id = existing?.id ?: "server-${change.id}",
            value = change.value,
            serverId = change.id,
            updatedAt = change.updatedAt,
            isDeleted = change.deleted,
            syncState = SyncState.SYNCED,
            localUpdatedAt = existing?.localUpdatedAt ?: 0L,
        )

    override suspend fun activeRows(): List<TestEntity> = rows.values.filter { !it.isDeleted }

    override suspend fun purgeSynced() {
        rows.values
            .filter { it.syncState == SyncState.SYNCED }
            .map { it.id }
            .forEach(rows::remove)
    }
}

internal fun testResponse(
    mode: String = SyncResponse.MODE_DELTA,
    applied: List<SyncAppliedRecord> = emptyList(),
    serverChanges: List<TestChange> = emptyList(),
    cursor: String? = null,
    hasMore: Boolean = false,
    fullResyncRequired: Boolean = false,
): SyncResponse<TestChange> =
    SyncResponse(
        syncVersion = 1,
        mode = mode,
        applied = applied,
        serverChanges = serverChanges,
        cursor = cursor,
        hasMore = hasMore,
        fullResyncRequired = fullResyncRequired,
    )
