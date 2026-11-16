package io.github.maniramezan.kommon.sync

/**
 * Optional telemetry hook for [SyncEngine]. All methods are no-ops by default ([NoOpSyncTelemetrySink])
 * so wiring analytics is opt-in; bridge this to your own analytics client (e.g. the `analytics-core`
 * module's `AnalyticsClient`) to get the same event vocabulary ExampleApp's `user_sync_*` events used.
 */
public interface SyncTelemetrySink {
    public fun onSyncStarted(resourceName: String)

    public fun onSyncCompleted(
        resourceName: String,
        mode: String,
        appliedCount: Int,
        changeCount: Int,
        durationMs: Long,
    )

    public fun onSyncFailed(
        resourceName: String,
        errorType: String,
        errorMessage: String?,
    )

    public fun onItemBlocked(
        resourceName: String,
        status: String,
        reason: String?,
    )

    public fun onFullResync(resourceName: String)

    /**
     * The pass for [resourceName] was cancelled after [onSyncStarted]. Cancellation is not a
     * failure, so [onSyncFailed] is not called. Defaults to a no-op so existing sinks compile.
     */
    public fun onSyncCancelled(resourceName: String): Unit = Unit
}

/** A [SyncTelemetrySink] that discards every event. Default when telemetry isn't wired up. */
public object NoOpSyncTelemetrySink : SyncTelemetrySink {
    override fun onSyncStarted(resourceName: String): Unit = Unit

    override fun onSyncCompleted(
        resourceName: String,
        mode: String,
        appliedCount: Int,
        changeCount: Int,
        durationMs: Long,
    ): Unit = Unit

    override fun onSyncFailed(
        resourceName: String,
        errorType: String,
        errorMessage: String?,
    ): Unit = Unit

    override fun onItemBlocked(
        resourceName: String,
        status: String,
        reason: String?,
    ): Unit = Unit

    override fun onFullResync(resourceName: String): Unit = Unit
}
