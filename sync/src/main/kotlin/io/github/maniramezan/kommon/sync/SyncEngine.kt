package io.github.maniramezan.kommon.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives offline, cross-device delta sync for user-owned resources through one generic loop.
 * Resources plug in as [SyncResourceAdapter]s; this engine owns the contract invariants (ack
 * guard, pending guard, full-snapshot tombstone reconciliation, pagination drain,
 * `fullResyncRequired`), so they hold identically for all resources and a new resource cannot
 * accidentally skip one.
 *
 * This is a plain, DI-framework-agnostic class: construct it with your own [SyncCursorStore] and
 * (optionally) a [SyncTelemetrySink], and wire it into whatever DI container your app uses.
 */
public class SyncEngine(
    private val cursorStore: SyncCursorStore,
    private val telemetry: SyncTelemetrySink = NoOpSyncTelemetrySink,
) {
    // Serialises sync passes so overlapping triggers can't double-apply.
    private val syncMutex = Mutex()

    /**
     * Runs [adapters] in sequence under one sync pass. Per-resource isolation: one resource
     * failing does not abort the others; the first failure (if any) is returned once all
     * adapters have been attempted.
     */
    public suspend fun syncAll(
        adapters: List<SyncResourceAdapter<*, *, *, *>>,
        limit: Int = DEFAULT_LIMIT,
    ): Result<Unit> =
        syncMutex.withLock {
            val errors = mutableListOf<Throwable>()
            for (adapter in adapters) {
                runCatching { drive(adapter, limit) }.onFailure { errors += it }
            }
            errors.firstOrNull()?.let { Result.failure(it) } ?: Result.success(Unit)
        }

    /** Runs a single resource's sync pass. */
    public suspend fun <E : SyncableEntity<E>, U, D, C> sync(
        adapter: SyncResourceAdapter<E, U, D, C>,
        limit: Int = DEFAULT_LIMIT,
    ): Result<Unit> = syncMutex.withLock { runCatching { drive(adapter, limit) } }

    @Suppress("TooGenericExceptionCaught") // Any failure must reach telemetry, then rethrow.
    private suspend fun <E : SyncableEntity<E>, U, D, C> drive(adapter: SyncResourceAdapter<E, U, D, C>, limit: Int) {
        val startedAt = System.currentTimeMillis()
        try {
            reportTelemetry { telemetry.onSyncStarted(adapter.resourceName) }
            val pendingByKey = adapter.pending().associateBy { adapter.syncKey(it) }
            var response = push(adapter, pendingByKey.values.toList(), limit)
            if (response.fullResyncRequired) response = fullResync(adapter, limit)

            applyAck(adapter, response.applied, pendingByKey)
            val activeKeys = mutableSetOf<String>()
            var fullMode = ingestChanges(adapter, response, activeKeys)
            storeMetadata(adapter.resourceName, response)
            var changeCount = response.serverChanges.size

            while (response.hasMore) {
                response = adapter.api(SyncRequest(since = cursorOf(adapter), limit = limit))
                fullMode = ingestChanges(adapter, response, activeKeys) || fullMode
                storeMetadata(adapter.resourceName, response)
                changeCount += response.serverChanges.size
            }
            if (fullMode) reconcileFullSnapshot(adapter, activeKeys)

            reportTelemetry {
                telemetry.onSyncCompleted(
                    adapter.resourceName,
                    response.mode,
                    response.applied.size,
                    changeCount,
                    System.currentTimeMillis() - startedAt,
                )
            }
        } catch (throwable: Throwable) {
            // Surface any failure to telemetry, then rethrow so syncAll's per-resource
            // isolation can record it without aborting the other resources.
            val errorType = throwable::class.simpleName ?: "Unknown"
            reportTelemetry { telemetry.onSyncFailed(adapter.resourceName, errorType, throwable.message) }
            throw throwable
        }
    }

    private suspend fun <E : SyncableEntity<E>, U, D, C> push(
        adapter: SyncResourceAdapter<E, U, D, C>,
        pending: List<E>,
        limit: Int,
    ): SyncResponse<C> =
        adapter.api(
            SyncRequest(
                since = cursorOf(adapter),
                limit = limit,
                upserts = pending.filterNot { it.isDeleted }.map { adapter.toUpsert(it) },
                deletes = pending.filter { it.isDeleted }.map { adapter.toDelete(it) },
            ),
        )

    /** Cursor too old to serve a delta: drop clean rows + cursor, then pull a fresh snapshot. */
    private suspend fun <E : SyncableEntity<E>, U, D, C> fullResync(
        adapter: SyncResourceAdapter<E, U, D, C>,
        limit: Int,
    ): SyncResponse<C> {
        adapter.purgeSynced()
        reportTelemetry { telemetry.onFullResync(adapter.resourceName) }
        return adapter.api(SyncRequest(since = null, limit = limit))
    }

    private suspend fun <E : SyncableEntity<E>, U, D, C> applyAck(
        adapter: SyncResourceAdapter<E, U, D, C>,
        applied: List<SyncAppliedRecord>,
        pendingByKey: Map<String, E>,
    ) {
        applied.forEach { ack ->
            val snapshot = pendingByKey[ack.key] ?: return@forEach
            // Ack guard: if the row was edited mid-flight, leave it pending to re-push.
            val current = adapter.reload(snapshot) ?: return@forEach
            if (current.localUpdatedAt != snapshot.localUpdatedAt) return@forEach
            adapter.upsert(current.applyOutcome(ack))
            if (ack.status == SyncAppliedRecord.STATUS_BLOCKED || ack.status == SyncAppliedRecord.STATUS_REJECTED) {
                reportTelemetry { telemetry.onItemBlocked(adapter.resourceName, ack.status, ack.reason) }
            }
        }
    }

    /** Applies one page's `serverChanges`; returns whether the page was a full snapshot. */
    private suspend fun <E : SyncableEntity<E>, U, D, C> ingestChanges(
        adapter: SyncResourceAdapter<E, U, D, C>,
        response: SyncResponse<C>,
        activeKeys: MutableSet<String>,
    ): Boolean {
        response.serverChanges.forEach { change ->
            if (!adapter.isChangeDeleted(change)) activeKeys += adapter.changeKey(change)
            val existing = adapter.findExisting(change)
            // Pending guard: never clobber a row that still has un-pushed local changes.
            if (existing != null && existing.syncState != SyncState.SYNCED) return@forEach
            adapter.upsert(adapter.toEntity(change, existing))
        }
        return response.mode == SyncResponse.MODE_FULL
    }

    private suspend fun <E : SyncableEntity<E>, U, D, C> reconcileFullSnapshot(
        adapter: SyncResourceAdapter<E, U, D, C>,
        activeKeys: Set<String>,
    ) {
        adapter.activeRows().forEach { entity ->
            // Tombstone guard: only clean rows absent from the snapshot; never delete
            // rows with un-pushed local changes.
            if (entity.syncState == SyncState.SYNCED && adapter.syncKey(entity) !in activeKeys) {
                adapter.upsert(entity.tombstone())
            }
        }
    }

    private suspend fun cursorOf(adapter: SyncResourceAdapter<*, *, *, *>): String? = cursorStore.get(adapter.resourceName)?.cursor

    private suspend fun storeMetadata(
        resourceName: String,
        response: SyncResponse<*>,
    ) {
        cursorStore.save(
            SyncCursorRecord(
                resourceName = resourceName,
                cursor = response.cursor,
                lastSyncedAt = System.currentTimeMillis(),
                fullResyncRequired = response.fullResyncRequired,
                metadataExtra = response.metadataExtra,
            ),
        )
    }

    /** Telemetry is observational and must never affect sync persistence or retry behavior. */
    private fun reportTelemetry(block: () -> Unit) {
        runCatching(block).onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    public companion object {
        public const val DEFAULT_LIMIT: Int = 100
    }
}

/** Applies an `applied[]` ack to a row. Top-level so every resource shares one state map. */
private fun <E : SyncableEntity<E>> E.applyOutcome(ack: SyncAppliedRecord): E {
    val id = ack.id ?: serverId
    val updated = ack.updatedAt ?: updatedAt
    return when (ack.status) {
        SyncAppliedRecord.STATUS_BLOCKED, SyncAppliedRecord.STATUS_REJECTED ->
            withSync(serverId, updatedAt, isDeleted, SyncState.BLOCKED)
        SyncAppliedRecord.STATUS_DELETED ->
            withSync(id, updated, isDeleted = true, syncState = SyncState.SYNCED)
        else ->
            withSync(id, updated, isDeleted = false, syncState = SyncState.SYNCED)
    }
}
