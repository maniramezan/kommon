package io.github.maniramezan.kommon.sync

/** The set of local sync states a [SyncableEntity] row can be in. */
public object SyncState {
    public const val SYNCED: String = "synced"
    public const val PENDING_CREATE: String = "pendingCreate"
    public const val PENDING_UPDATE: String = "pendingUpdate"
    public const val PENDING_DELETE: String = "pendingDelete"
    public const val BLOCKED: String = "blocked"
}
