package io.github.maniramezan.kommon.sync

/**
 * Resource-specific glue for the generic loop in [SyncEngine]. Implement one of these (plus the
 * wire DTO mapping) to make a new resource syncable — the contract logic (ack guard, pending
 * guard, full-snapshot reconciliation, pagination drain, `fullResyncRequired`) lives in the
 * engine, not here, so it holds identically for every resource.
 *
 * @param E entity type (carries [SyncableEntity] bookkeeping)
 * @param U upsert DTO, [D] delete DTO, [C] server-change DTO
 */
@Suppress("TooManyFunctions") // One cohesive port for the sync loop; splitting it would obscure intent.
public interface SyncResourceAdapter<E : SyncableEntity<E>, U, D, C> {
    public val resourceName: String

    public suspend fun pending(): List<E>

    /** Canonical business key; MUST equal the server's `applied[].key` and [changeKey]. */
    public fun syncKey(entity: E): String

    public fun toUpsert(entity: E): U

    public fun toDelete(entity: E): D

    public suspend fun api(request: SyncRequest<U, D>): SyncResponse<C>

    /** Re-read the row for the ack guard (detects edits made during the in-flight request). */
    public suspend fun reload(entity: E): E?

    public suspend fun upsert(entity: E)

    public suspend fun findExisting(change: C): E?

    public fun changeKey(change: C): String

    public fun isChangeDeleted(change: C): Boolean

    public fun toEntity(
        change: C,
        existing: E?,
    ): E

    /** Active (non-deleted) rows, for full-snapshot tombstone reconciliation. */
    public suspend fun activeRows(): List<E>

    /** Drop clean synced rows for `fullResyncRequired`. */
    public suspend fun purgeSynced()
}
