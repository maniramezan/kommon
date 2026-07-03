package io.github.maniramezan.kommon.testing

import io.github.maniramezan.kommon.sync.SyncCursorRecord
import io.github.maniramezan.kommon.sync.SyncCursorStore

/** In-memory [SyncCursorStore] fake for consumer unit tests, avoiding a Room/mocking round-trip. */
public class FakeSyncCursorStore : SyncCursorStore {
    private val records = mutableMapOf<String, SyncCursorRecord>()

    override suspend fun get(resourceName: String): SyncCursorRecord? = records[resourceName]

    override suspend fun save(record: SyncCursorRecord) {
        records[record.resourceName] = record
    }

    public fun recordFor(resourceName: String): SyncCursorRecord? = records[resourceName]
}
