package io.github.maniramezan.kommon.analytics.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** The kind of [AnalyticsClient][io.github.maniramezan.kommon.analytics.AnalyticsClient] call an [AnalyticsDebugEntry] captured. */
public enum class AnalyticsDebugAction {
    TRACK,
    TRACK_SCREEN,
    IDENTIFY,
    SET_USER_PROPERTIES,
    INCREMENT,
    RESET,
    FLUSH,
}

/** One captured analytics call, for an in-app debug screen. */
public data class AnalyticsDebugEntry(
    val id: Long = 0,
    val timestampMs: Long,
    val action: AnalyticsDebugAction,
    val name: String,
    val properties: Map<String, Any>,
)

/** Ring buffer of recent analytics calls, observable via [entries]. */
public interface AnalyticsDebugStore {
    public val entries: StateFlow<List<AnalyticsDebugEntry>>

    public fun append(entry: AnalyticsDebugEntry)

    public fun clear()
}

/**
 * Bounded in-memory [AnalyticsDebugStore] backed by a [StateFlow].
 *
 * The oldest entry is evicted when [capacity] is reached, preventing debug capture from growing
 * for the entire app process lifetime.
 */
public class InMemoryAnalyticsDebugStore(
    private val capacity: Int = DEFAULT_CAPACITY,
) : AnalyticsDebugStore {
    init {
        require(capacity > 0) { "capacity must be greater than zero" }
    }

    private val _entries = MutableStateFlow<List<AnalyticsDebugEntry>>(emptyList())
    private val nextId = AtomicLong(1L)

    override val entries: StateFlow<List<AnalyticsDebugEntry>> = _entries.asStateFlow()

    override fun append(entry: AnalyticsDebugEntry) {
        val entryWithId = entry.copy(id = nextId.getAndIncrement())
        _entries.update { current -> (current + entryWithId).takeLast(capacity) }
    }

    override fun clear() {
        _entries.value = emptyList()
    }

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 100
    }
}
