# kommon

Reusable Kotlin-first Android building blocks extracted from production apps. `kommon` is
provider- and DI-framework-agnostic: consumers bridge its contracts to their own persistence,
authentication, analytics, remote-config, and telemetry SDKs.

## Requirements

- Android library consumers using JDK 17 or newer.
- Kotlin 2.4.x and Android Gradle Plugin 9.2.x are used to build this release.

## Modules

| Artifact | Purpose |
| --- | --- |
| `foundation` | `BaseViewModel`, loading state, email validation, and logging seam. |
| `sync` | Offline delta-sync engine with pagination, acknowledgements, and tombstones. |
| `authsession` | Provider-agnostic auth and cold-start session contracts. |
| `analytics-core` / `analytics-debug` | Analytics port, payload sanitizer, and bounded debug capture. |
| `crashreporting` / `telemetry` | Crash-reporting and OpenTelemetry-shaped ports. |
| `remoteconfig` / `remoteconfig-debug` | Typed remote-config contracts and debug-only local overrides. |
| `parsing` | Localized-value and JSON string-array parsers. |
| `testing` | Consumer test fakes for sync, analytics, and remote config. |

## Installation

```kotlin
dependencies {
    implementation("io.github.maniramezan.kommon:sync:<version>")
    testImplementation("io.github.maniramezan.kommon:testing:<version>")
}
```

Artifacts are published independently. Use only the modules your app needs.

## Sync Integration

Implement `SyncResourceAdapter` around one local resource and its API DTOs, then invoke the
engine from your app's sync coordinator:

```kotlin
val result = SyncEngine(cursorStore).sync(tasksAdapter)
```

`syncKey` must be stable and exactly match both the server's `applied[].key` and every server
change key. Make adapter writes and cursor persistence transactional in the consumer app where
the storage layer permits it. Keep tombstones until the server acknowledges deletion; clean-up
policy belongs to the consuming app. The engine serializes calls made through one instance.

## Privacy

Analytics properties, debug captures, logs, and crash-reporting context can contain sensitive
data. Do not include credentials, tokens, email addresses, or other personal data. The analytics
debug store is in-memory and bounded, but should be wired only into debug tooling and protected
from production users.

## Compatibility

The library is pre-1.0. Breaking changes may occur between minor releases and are called out in
release notes. Public APIs use explicit visibility and are documented through the published Dokka
site.

## Development

```bash
./gradlew check
./gradlew dokkaGenerate
```
