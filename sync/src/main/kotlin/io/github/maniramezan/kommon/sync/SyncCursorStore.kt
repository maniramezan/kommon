package io.github.maniramezan.kommon.sync

/**
 * Cursor/quota bookkeeping persisted per resource, keyed by [SyncResourceAdapter.resourceName].
 *
 * A minimal Room-backed implementation typically wraps a single-table DAO keyed by
 * `resourceName`, mirroring the shape of [SyncCursorRecord].
 */
public interface SyncCursorStore {
    public suspend fun get(resourceName: String): SyncCursorRecord?

    public suspend fun save(record: SyncCursorRecord)
}

/** One resource's persisted cursor/quota bookkeeping. */
public data class SyncCursorRecord(
    val resourceName: String,
    val cursor: String? = null,
    val lastSyncedAt: Long? = null,
    val fullResyncRequired: Boolean = false,
    /** Mirrors [SyncResponse.metadataExtra] verbatim; opaque to the engine. */
    val metadataExtra: Map<String, String> = emptyMap(),
)
