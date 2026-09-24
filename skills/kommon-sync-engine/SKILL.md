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
| **Pagination drain**: follow `hasMore` using each page's cursor | `drive` | `delta pagination drains...`, `pagination requests each page...` |
| **Non-advancing cursor guard**: `hasMore` with a missing/repeated cursor fails instead of looping | `drive` | `pagination guard fails...` |
| `fullResyncRequired`: apply push acks, purge clean rows, re-pull from `since = null` | `fullResync`, `drive` | `full resync purges...`, `full resync applies push acks...` |
| **Per-resource isolation**: one failing adapter doesn't stop others; first error returned, rest suppressed | `syncAll` | `syncAll isolates...`, `...suppressed` |
| **Cancellation transparency**: `CancellationException` propagates, never becomes `Result.failure` or `onSyncFailed` | `runCatchingNonCancellation`, `drive` | `cancellation propagates...` |
| Telemetry is observational: sink exceptions never fail a sync | `reportTelemetry` | `telemetry failures do not fail sync` |
| `appliedCount` in `onSyncCompleted` counts first-page acks | `drive` | `completed telemetry reports first-page acks...` |
| Passes are serialized per engine instance | `syncMutex` | — |

## Rules

- Wire-format changes to `SyncRequest`/`SyncResponse`/`SyncAppliedRecord` are **server contract
  changes**: keep `@SerialName`s stable, add new fields with defaults, and call it out in the PR.
- New adapter hooks must have a default implementation or be flagged as a breaking change (every
  consumer implements `SyncResourceAdapter`).
- Use the injected `clockMs`, never `System.currentTimeMillis()` directly.
- Test with `TestResourceAdapter` + mocked `TestApi` (`SyncEngineTestFixtures.kt`); keep fixtures
  generic — never import app types.
