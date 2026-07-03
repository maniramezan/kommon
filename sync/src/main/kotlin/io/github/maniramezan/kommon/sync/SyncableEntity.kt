package io.github.maniramezan.kommon.sync

/**
 * Implemented by local entities/rows that participate in delta sync.
 *
 * A [SyncEngine] never mutates domain fields directly — it only reads/writes this bookkeeping
 * envelope via [withSync], so an entity's own columns are free to be anything the app needs.
 */
public interface SyncableEntity<T : SyncableEntity<T>> {
    public val serverId: Int?
    public val updatedAt: Long?
    public val isDeleted: Boolean
    public val syncState: String
    public val localUpdatedAt: Long

    public fun withSync(
        serverId: Int?,
        updatedAt: Long?,
        isDeleted: Boolean,
        syncState: String,
    ): T

    /** Marks this row deleted-and-synced, used by full-snapshot tombstone reconciliation. */
    public fun tombstone(): T = withSync(serverId, updatedAt, isDeleted = true, syncState = SyncState.SYNCED)
}
