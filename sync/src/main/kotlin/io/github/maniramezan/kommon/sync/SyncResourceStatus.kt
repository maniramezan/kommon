package io.github.maniramezan.kommon.sync

/** The latest known state of one resource's sync pass, as tracked by [SyncStatusTracker]. */
public sealed interface SyncResourceStatus {
    /** A pass started at [startedAtMs] and has not finished yet. */
    public data class Syncing(
        val startedAtMs: Long,
    ) : SyncResourceStatus

    /** The last pass finished at [completedAtMs]; the counts match [SyncTelemetrySink.onSyncCompleted]. */
    public data class Succeeded(
        val completedAtMs: Long,
        val mode: String,
        val appliedCount: Int,
        val changeCount: Int,
        val durationMs: Long,
    ) : SyncResourceStatus

    /**
     * The last pass failed at [failedAtMs]. [errorMessage] is the exception message, which can
     * contain server text; do not show it to users or log it without review.
     */
    public data class Failed(
        val failedAtMs: Long,
        val errorType: String,
        val errorMessage: String?,
    ) : SyncResourceStatus

    /** The last pass was cancelled at [cancelledAtMs] before it finished. */
    public data class Cancelled(
        val cancelledAtMs: Long,
    ) : SyncResourceStatus
}
