package io.github.maniramezan.kommon.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [SyncTelemetrySink] that keeps the latest [SyncResourceStatus] of every resource in an
 * observable [statuses] map, for "syncing…" indicators, "last synced" labels, and retry prompts.
 * Every event is then forwarded to [delegate], so it composes with an existing analytics sink:
 *
 * ```kotlin
 * val status = SyncStatusTracker(delegate = analyticsSyncTelemetry)
 * val engine = SyncEngine(cursorStore, telemetry = status)
 *
 * status.statuses.map { it.values.any { s -> s is SyncResourceStatus.Syncing } }
 * ```
 *
 * A resource is absent from [statuses] until its first pass starts. The state is in memory only;
 * persisted "last synced" times stay in [SyncCursorRecord.lastSyncedAt].
 *
 * @param clockMs wall-clock source for status timestamps; override in tests.
 */
public class SyncStatusTracker(
    private val delegate: SyncTelemetrySink = NoOpSyncTelemetrySink,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : SyncTelemetrySink {
    private val _statuses = MutableStateFlow<Map<String, SyncResourceStatus>>(emptyMap())

    /** The latest status per [SyncResourceAdapter.resourceName]. */
    public val statuses: StateFlow<Map<String, SyncResourceStatus>> = _statuses.asStateFlow()

    /** The latest status of [resourceName], or `null` when it has not synced in this process. */
    public fun status(resourceName: String): SyncResourceStatus? = _statuses.value[resourceName]

    override fun onSyncStarted(resourceName: String) {
        record(resourceName, SyncResourceStatus.Syncing(clockMs()))
        delegate.onSyncStarted(resourceName)
    }

    override fun onSyncCompleted(
        resourceName: String,
        mode: String,
        appliedCount: Int,
        changeCount: Int,
        durationMs: Long,
    ) {
        record(resourceName, SyncResourceStatus.Succeeded(clockMs(), mode, appliedCount, changeCount, durationMs))
        delegate.onSyncCompleted(resourceName, mode, appliedCount, changeCount, durationMs)
    }

    override fun onSyncFailed(
        resourceName: String,
        errorType: String,
        errorMessage: String?,
    ) {
        record(resourceName, SyncResourceStatus.Failed(clockMs(), errorType, errorMessage))
        delegate.onSyncFailed(resourceName, errorType, errorMessage)
    }

    override fun onSyncCancelled(resourceName: String) {
        record(resourceName, SyncResourceStatus.Cancelled(clockMs()))
        delegate.onSyncCancelled(resourceName)
    }

    override fun onItemBlocked(
        resourceName: String,
        status: String,
        reason: String?,
    ) {
        delegate.onItemBlocked(resourceName, status, reason)
    }

    override fun onFullResync(resourceName: String) {
        delegate.onFullResync(resourceName)
    }

    private fun record(
        resourceName: String,
        status: SyncResourceStatus,
    ) {
        _statuses.update { it + (resourceName to status) }
    }
}
