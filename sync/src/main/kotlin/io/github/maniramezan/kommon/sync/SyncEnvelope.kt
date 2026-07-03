package io.github.maniramezan.kommon.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire request envelope for one sync pass against a single resource. */
@Serializable
public data class SyncRequest<Upsert, Delete>(
    @SerialName("since")
    val since: String? = null,
    @SerialName("limit")
    val limit: Int = SyncEngine.DEFAULT_LIMIT,
    @SerialName("upserts")
    val upserts: List<Upsert> = emptyList(),
    @SerialName("deletes")
    val deletes: List<Delete> = emptyList(),
)

/** Wire response envelope for one sync pass against a single resource. */
@Serializable
public data class SyncResponse<Change>(
    @SerialName("syncVersion")
    val syncVersion: Int,
    @SerialName("mode")
    val mode: String,
    @SerialName("applied")
    val applied: List<SyncAppliedRecord> = emptyList(),
    @SerialName("serverChanges")
    val serverChanges: List<Change> = emptyList(),
    @SerialName("cursor")
    val cursor: String? = null,
    @SerialName("hasMore")
    val hasMore: Boolean = false,
    @SerialName("fullResyncRequired")
    val fullResyncRequired: Boolean = false,
    /**
     * Free-form pass-through metadata (e.g. quota/subscription-tier hints) that the engine
     * itself never reads. Apps can stash product-specific fields here and read them back out of
     * [SyncCursorStore] without the generic engine needing to know they exist.
     */
    @SerialName("metadataExtra")
    val metadataExtra: Map<String, String> = emptyMap(),
) {
    public companion object {
        public const val MODE_FULL: String = "full"
        public const val MODE_DELTA: String = "delta"
    }
}

/** One row's outcome from the server for a pushed upsert/delete. */
@Serializable
public data class SyncAppliedRecord(
    @SerialName("key")
    val key: String,
    @SerialName("id")
    val id: Int? = null,
    @SerialName("status")
    val status: String,
    @SerialName("updatedAt")
    val updatedAt: Long? = null,
    @SerialName("reason")
    val reason: String? = null,
) {
    public companion object {
        public const val STATUS_BLOCKED: String = "blocked"
        public const val STATUS_REJECTED: String = "rejected"
        public const val STATUS_DELETED: String = "deleted"
    }
}
