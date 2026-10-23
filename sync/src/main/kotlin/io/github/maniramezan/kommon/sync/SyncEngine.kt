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
 *
 * Coroutine cancellation is never converted into a failed [Result]: cancelling the caller cancels
 * the pass and rethrows [CancellationException], and is not reported as a sync failure.
 *
 * @param clockMs wall-clock source for `lastSyncedAt` and telemetry durations; override in tests.
 */
public class SyncEngine(
    private val cursorStore: SyncCursorStore,
    private val telemetry: SyncTelemetrySink = NoOpSyncTelemetrySink,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    // Serialises sync passes so overlapping triggers can't double-apply.
    private val syncMutex = Mutex()

    /**
     * Runs [adapters] in sequence under one sync pass. Per-resource isolation: one resource
     * failing does not abort the others; the first failure (if any) is returned once all
     * adapters have been attempted, with later failures attached via [Throwable.addSuppressed].
     */
    public suspend fun syncAll(
        adapters: List<SyncResourceAdapter<*, *, *, *>>,
        limit: Int = DEFAULT_LIMIT,
    ): Result<Unit> = syncAll(adapters, limit, pushBatchSize = Int.MAX_VALUE)

    /**
     * Runs all resources with opt-in bounded pushes. See [sync] for the server requirements
     * and the distinction between [limit] and [pushBatchSize]. Failures remain isolated per resource.
     */
    public suspend fun syncAll(
        adapters: List<SyncResourceAdapter<*, *, *, *>>,
        limit: Int = DEFAULT_LIMIT,
        pushBatchSize: Int,
    ): Result<Unit> {
        require(limit > 0) { "limit must be greater than zero" }
        require(pushBatchSize > 0) { "pushBatchSize must be greater than zero" }
        return syncMutex.withLock {
            var firstError: Throwable? = null
            for (adapter in adapters) {
                runCatchingNonCancellation { drive(adapter, limit, pushBatchSize) }.onFailure { error ->
                    val first = firstError
                    when {
                        first == null -> firstError = error
                        first !== error -> first.addSuppressed(error)
                    }
                }
            }
            firstError?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }

    /** Runs a single resource's sync pass with one unbounded push and response pages of [limit] rows. */
    public suspend fun <E : SyncableEntity<E>, U, D, C> sync(
        adapter: SyncResourceAdapter<E, U, D, C>,
        limit: Int = DEFAULT_LIMIT,
    ): Result<Unit> = sync(adapter, limit, pushBatchSize = Int.MAX_VALUE)

    /**
     * Runs a resource with at most [pushBatchSize] pending rows per request, counting upserts
     * and deletes together. [limit] still controls response page size and is sent unchanged.
     *
     * Enable only when the server accepts independent partial pushes and retries by stable row
     * keys. Each batch's acknowledgements, response pages, and full-snapshot reconciliation finish
     * before the next batch starts from the saved cursor. A failure stops this resource; already
     * persisted batches remain committed, and pending rows can be retried on the next pass.
     *
     * Pending rows are snapshotted once per pass, bounding its work even if local edits continue.
     * Rows edited in flight remain pending through the acknowledgement guard. This bounds network
     * request size; [SyncResourceAdapter.pending] still loads the complete pending list into memory.
     * Empty pending lists still issue one pull request. Telemetry reports totals across all batches.
     */
    public suspend fun <E : SyncableEntity<E>, U, D, C> sync(
        adapter: SyncResourceAdapter<E, U, D, C>,
        limit: Int = DEFAULT_LIMIT,
        pushBatchSize: Int,
    ): Result<Unit> {
        require(limit > 0) { "limit must be greater than zero" }
        require(pushBatchSize > 0) { "pushBatchSize must be greater than zero" }
        return syncMutex.withLock { runCatchingNonCancellation { drive(adapter, limit, pushBatchSize) } }
    }

    @Suppress("TooGenericExceptionCaught") // Any failure must reach telemetry, then rethrow.
    private suspend fun <E : SyncableEntity<E>, U, D, C> drive(
        adapter: SyncResourceAdapter<E, U, D, C>,
        limit: Int,
        pushBatchSize: Int,
    ) {
        val startedAt = clockMs()
        try {
            reportTelemetry { telemetry.onSyncStarted(adapter.resourceName) }
            val pending =
                adapter
                    .pending()
                    .associateBy { adapter.syncKey(it) }
                    .values
                    .toList()
            val batches = pending.chunked(pushBatchSize).ifEmpty { listOf(emptyList()) }
            var appliedCount = 0
            var changeCount = 0
            var mode = SyncResponse.MODE_DELTA
            for (batch in batches) {
                val pushResponse = push(adapter, batch, limit)
                applyAck(adapter, pushResponse.applied, batch.associateBy { adapter.syncKey(it) })
                appliedCount += pushResponse.applied.size
                val pulled = drainChanges(adapter, pushResponse, limit)
                changeCount += pulled.changeCount
                mode = pulled.mode
            }

            reportTelemetry {
                telemetry.onSyncCompleted(
                    adapter.resourceName,
                    mode,
                    appliedCount,
                    changeCount,
                    clockMs() - startedAt,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // Surface any failure to telemetry, then rethrow so syncAll's per-resource
            // isolation can record it without aborting the other resources.
            val errorType = throwable::class.simpleName ?: "Unknown"
            reportTelemetry { telemetry.onSyncFailed(adapter.resourceName, errorType, throwable.message) }
            throw throwable
        }
    }

    private suspend fun <E : SyncableEntity<E>, U, D, C> drainChanges(
        adapter: SyncResourceAdapter<E, U, D, C>,
        pushResponse: SyncResponse<C>,
        limit: Int,
    ): PullSummary {
        var response = if (pushResponse.fullResyncRequired) fullResync(adapter, limit) else pushResponse
        // A snapshot is only complete when it starts on this pass's first page; reconciling a
        // snapshot resumed mid-way would tombstone every row served by the pages already seen.
        val fullSnapshot = response.mode == SyncResponse.MODE_FULL
        val activeKeys = mutableSetOf<String>()
        ingestChanges(adapter, response, activeKeys)
        // Snapshot cursors are persisted only once the whole snapshot has been applied, so a
        // failure mid-drain restarts the snapshot instead of resuming it without reconciliation.
        if (!fullSnapshot) storeMetadata(adapter.resourceName, response)
        var changeCount = response.serverChanges.size

        val requestedCursors = mutableSetOf<String>()
        while (response.hasMore) {
            val since = response.cursor
            // Pagination guard: a server that keeps answering hasMore without advancing the
            // cursor would otherwise spin this loop forever.
            check(since != null) { "Sync '${adapter.resourceName}': hasMore=true without a cursor" }
            requestedCursors += since
            response = adapter.api(SyncRequest(since = since, limit = limit))
            check(!response.hasMore || response.cursor !in requestedCursors) {
                "Sync '${adapter.resourceName}': cursor did not advance past '$since'"
            }
            ingestChanges(adapter, response, activeKeys)
            if (!fullSnapshot) storeMetadata(adapter.resourceName, response)
            changeCount += response.serverChanges.size
        }
        if (fullSnapshot) {
            reconcileFullSnapshot(adapter, activeKeys)
            storeMetadata(adapter.resourceName, response)
        }
        return PullSummary(response.mode, changeCount)
    }

    private data class PullSummary(
        val mode: String,
        val changeCount: Int,
    )

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
        val acknowledged =
            applied.mapNotNull { ack ->
                val snapshot = pendingByKey[ack.key] ?: return@mapNotNull null
                // Ack guard: if the row was edited mid-flight, leave it pending to re-push.
                val current = adapter.reload(snapshot) ?: return@mapNotNull null
                if (current.localUpdatedAt != snapshot.localUpdatedAt) return@mapNotNull null
                if (ack.status == SyncAppliedRecord.STATUS_BLOCKED || ack.status == SyncAppliedRecord.STATUS_REJECTED) {
                    reportTelemetry { telemetry.onItemBlocked(adapter.resourceName, ack.status, ack.reason) }
                }
                current.applyOutcome(ack)
            }
        if (acknowledged.isNotEmpty()) adapter.upsertAll(acknowledged)
    }

    /**
     * Applies one page's `serverChanges` in a single [SyncResourceAdapter.upsertAll] batch.
     * Repeated keys are merged in response order before the final entity per key is written.
     */
    private suspend fun <E : SyncableEntity<E>, U, D, C> ingestChanges(
        adapter: SyncResourceAdapter<E, U, D, C>,
        response: SyncResponse<C>,
        activeKeys: MutableSet<String>,
    ) {
        val incoming = linkedMapOf<String, E>()
        response.serverChanges.forEach { change ->
            val key = adapter.changeKey(change)
            if (!adapter.isChangeDeleted(change)) activeKeys += key
            val existing = incoming[key] ?: adapter.findExisting(change)
            if (existing != null && existing.syncState != SyncState.SYNCED) return@forEach
            incoming[key] = adapter.toEntity(change, existing)
        }
        if (incoming.isNotEmpty()) adapter.upsertAll(incoming.values.toList())
    }

    private suspend fun <E : SyncableEntity<E>, U, D, C> reconcileFullSnapshot(
        adapter: SyncResourceAdapter<E, U, D, C>,
        activeKeys: Set<String>,
    ) {
        // Tombstone guard: only clean rows absent from the snapshot; never delete
        // rows with un-pushed local changes.
        val tombstones =
            adapter
                .activeRows()
                .filter { it.syncState == SyncState.SYNCED && adapter.syncKey(it) !in activeKeys }
                .map { it.tombstone() }
        if (tombstones.isNotEmpty()) adapter.upsertAll(tombstones)
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
                lastSyncedAt = clockMs(),
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

/** Like [runCatching], but lets [CancellationException] propagate so structured concurrency holds. */
@Suppress("TooGenericExceptionCaught")
private inline fun runCatchingNonCancellation(block: () -> Unit): Result<Unit> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
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
        // Any other status confirms the push as sent, so a pending delete stays deleted.
        else ->
            withSync(id, updated, isDeleted = isDeleted, syncState = SyncState.SYNCED)
    }
}
