---
name: kommon-sync-engine
description: Invariant checklist and test pattern for changing kommon's :sync module (SyncEngine, SyncResourceAdapter, SyncEnvelope, SyncCursorStore, telemetry). Use before modifying anything under sync/.
---

# Changing `:sync`

`SyncEngine` is the highest-value module; consumers rely on these invariants holding for every
resource. Each one has a named test in `SyncEngineTest` — if you change behavior, change or add the
matching test in the same commit.

| Invariant | Where | Test |
| --- | --- | --- |
| Pending rows are pushed (upserts vs deletes split on `isDeleted`) | `push` | `sync sends pending upsert...` |
| **Ack guard**: an ack never demotes a row edited mid-flight (`localUpdatedAt` changed) | `applyAck` | `ack guard leaves row pending...` |
| Blocked/rejected acks mark only that row `BLOCKED` | `applyOutcome` | `blocked rows are marked blocked...` |
| **Pending guard**: server changes never clobber rows with unpushed local changes | `ingestChanges` | covered by blocked/ack tests |
| **Tombstone guard**: full snapshots tombstone only clean rows absent from the snapshot | `reconcileFullSnapshot` | `full snapshot tombstones...` |
| **Snapshot integrity**: a snapshot is reconciled only when it starts on the pass's first page, and its cursor is persisted only after every page is applied | `drive` | `snapshot cursor is not persisted...`, `paginated snapshot reconciles...`, `full page after a delta first page...` |
| A pending delete stays deleted when acked with any non-blocked status | `applyOutcome` | `pending delete acknowledged...` |
| Repeated server changes for a key merge in response order before batching | `ingestChanges` | `repeated changes in one page...` |
| Writes are batched through `upsertAll` (acks, each page, tombstones) | `applyAck`, `ingestChanges`, `reconcileFullSnapshot` | `each page of server changes...` |
| **Pagination drain**: follow `hasMore` using each page's cursor | `drive` | `delta pagination drains...`, `pagination requests each page...` |
| **Non-advancing cursor guard**: `hasMore` with a missing/repeated cursor fails instead of looping | `drive` | `pagination guard fails...` |
| `fullResyncRequired`: apply push acks, purge clean rows, re-pull from `since = null` | `fullResync`, `drive` | `full resync purges...`, `full resync applies push acks...` |
| **Per-resource isolation**: one failing adapter doesn't stop others; first error returned, rest suppressed | `syncAll` | `syncAll isolates...`, `...suppressed` |
| **Cancellation transparency**: `CancellationException` propagates, never becomes `Result.failure` or `onSyncFailed` | `runCatchingNonCancellation`, `drive` | `cancellation propagates...` |
| Telemetry is observational: sink exceptions never fail a sync | `reportTelemetry` | `telemetry failures do not fail sync` |
| `appliedCount` in `onSyncCompleted` counts first-page acks | `drive` | `completed telemetry reports first-page acks...` |
| Opt-in push batches bound upserts + deletes together, fully drain each response, and resume from its committed cursor | `drive`, `drainChanges` | `SyncEngineBatchingTest` |
| Legacy overloads keep one unbounded push; batching snapshots pending rows once and scopes acks to the sent batch | `sync`, `syncAll`, `drive` | `legacy sync pushes...`, `acknowledgements cannot...`, `mid-flight edits...` |
| Passes are serialized per engine instance | `syncMutex` | — |

## Rules

- Wire-format changes to `SyncRequest`/`SyncResponse`/`SyncAppliedRecord` are **server contract
  changes**: keep `@SerialName`s stable, add new fields with defaults, and call it out in the PR.
- New adapter hooks must have a default implementation or be flagged as a breaking change (every
  consumer implements `SyncResourceAdapter`).
- Use the injected `clockMs`, never `System.currentTimeMillis()` directly.
- `limit` is the response-page bound. Only the explicit `pushBatchSize` overloads split pushes;
  consumers must confirm their server accepts independent partial pushes and stable-key retries.
  Each batch must finish its full response drain before the next push; snapshot active-key sets
  belong to one drain, and telemetry totals span the whole resource pass.
- Test with `TestResourceAdapter` + mocked `TestApi` (`SyncEngineTestFixtures.kt`); keep fixtures
  generic — never import app types.
