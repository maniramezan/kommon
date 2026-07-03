# kommon Agent Instructions

## Purpose

This repo owns a reusable, Hilt-free, Kotlin-first Android/JVM helper library extracted from
production apps (initially Novalingo). Keep the library product-agnostic and reusable across
projects: generic contracts and algorithms live here; app-specific domain models, concrete DTOs,
Room entities/DAOs, and DI wiring stay in the consuming app.

`AGENTS.md` is the source of truth for agent guidance. Keep `CLAUDE.md` thin and point back to
this file.

## Required Skill Loading

Load the best matching installed skill before non-trivial planning, implementation, or review
work:

- `android-gradle-logic` for Gradle convention plugins, version catalogs, and module wiring.
- `gradle-build-performance` for slow Gradle builds, CI time, or dependency graph cost.
- `kotlin-backend-jpa-entity-mapping` if a future module touches JPA/Room entity design.
- `kotlin-tooling-java-to-kotlin` if porting more Java-era Novalingo code into this repo.

When adding or updating dependencies, check the latest stable version online first when network
access is available, then pin the verified version in `gradle/libs.versions.toml`.

## Repository Map

- `:foundation` — `BaseViewModel`, `LoadingState`/`LoadingError`, the `KommonLogger` logging seam
  (+ `TimberLogger` bridge), `isValidEmail()`. No other module dependencies.
- `:sync` — the generic delta-sync engine: `SyncEngine`, `SyncResourceAdapter`, `SyncableEntity`/
  `SyncState`, the `SyncRequest`/`SyncResponse` wire envelope, `SyncCursorStore`, `SyncTelemetrySink`.
  Depends on `:foundation` only. This is the highest-value, most-tested module — treat its
  invariants (ack guard, pending guard, tombstone reconciliation, pagination drain, per-resource
  isolation) as load-bearing; see the KDoc on `SyncEngine` and `SyncEngineTest` before changing it.
- `:authsession` — `AuthRepository`, `AuthUser`, `AuthSessionStore`, `AuthSessionInitializer`,
  `authSignInErrorMessage()`. Depends on `:foundation` for the logging seam.
- `:analytics-core` — `AnalyticsClient` port, `AnalyticsEvent`, `NoOp`/`Logging`/
  `CompositeAnalyticsClient`, `AnalyticsPayloadSanitizer`. Depends on `:foundation`.
- `:analytics-debug` — `AnalyticsDebugStore`, `AnalyticsCaptureClient` decorator. Depends on
  `:analytics-core`.
- `:crashreporting` — `CrashlyticsClient` port, `NoOpCrashlyticsClient`, `RecordingCrashlyticsClient`
  (a test double shipped in `main`, not `test`, so consumer apps can use it directly). No deps.
- `:telemetry` — `OpenTelemetryClient` port + `AnalyticsOpenTelemetryClient` bridge. Depends on
  `:analytics-core`.
- `:remoteconfig` — `RemoteConfigClient` port, `ConfigKey`/`ConfigValue`/`ConfigValueType`,
  `ResolvedConfigEntry`/`ValueSource`. Deliberately carries **no** built-in key registry — apps own
  their own `object AppConfigKeys { val ALL_KEYS = ... }`.
- `:remoteconfig-debug` — `LocalOverrideStore` (SharedPreferences-backed debug override store).
  Depends on `:remoteconfig` and `:foundation`.
- `:parsing` — `LocalizedValueParser`, `JsonEncodedStringArrayParser`. No deps beyond
  kotlinx-serialization.
- `:testing` — fakes for consumer unit tests (`FakeSyncCursorStore`, `RecordingAnalyticsClient`,
  `FakeRemoteConfigClient`). Depends on `:sync`, `:analytics-core`, `:remoteconfig` as `api` so
  consumers get them transitively on their test classpath.
- `build-logic/` — Gradle convention plugins (`kommon.android.library`, `kommon.kotlin.library`).
- `config/detekt/detekt.yml` — shared detekt overrides.

## Current Architecture Decisions

- **Extraction posture**: only genuinely generic, domain-agnostic code belongs here. When porting
  something new from a product app, split it the way `:sync`/`:remoteconfig` were split — generic
  contract/algorithm in `kommon`, concrete adapters/DTOs/entities/product fields stay in the app.
  See the extraction rationale recorded in the PR/commit history for `:sync`, `:remoteconfig`, and
  `:authsession` for worked examples of exactly where that line was drawn.
- **DI-agnostic by design**: no module depends on Hilt, Koin, or any other DI framework. Classes
  take plain constructor parameters; consumers wire them with whatever DI they use. Do not add
  `@Inject`/`@Singleton` annotations to library classes.
- **Logging seam, not a concrete logger**: library classes that need to log take a `KommonLogger`
  (default `NoOpLogger`) from `:foundation` rather than importing Timber/android.util.Log directly.
  `TimberLogger` is the one concrete bridge implementation, and it lives in `:foundation` precisely
  so consumers can opt in without forcing Timber on everyone else.
- **No networking or Compose UI here**: HTTP transport belongs in `kenwork`
  (`io.github.maniramezan.kenwork:*`) and Compose design-system primitives belong in
  `ComposeUIComponents` (`io.github.maniramezan.compose:*`). Do not duplicate either concern in
  this repo — if something looks like it belongs in one of those, extract it there instead
  (`ComposeUIComponents` can depend on/reference `kommon` where useful).
- The library is pre-1.0 (`0.1.0` and climbing); breaking API changes are still acceptable but
  should be called out explicitly, since Novalingo is the intended first consumer.

## Build Logic And Dependency Shape

- Shared Gradle behavior lives in `build-logic/convention` (`AndroidLibraryConventionPlugin`,
  `KotlinLibraryConventionPlugin`, `Jacoco.kt`). Reuse those conventions instead of duplicating
  Android/Kotlin setup in module build files.
- Module inclusion lives in `settings.gradle.kts`; update it when module topology changes.
- Version pins live in `gradle/libs.versions.toml`; update the catalog instead of hardcoding
  versions in build scripts.
- Every module applies `kommon.android.library` even where the code is pure Kotlin (matches the
  sibling `kenwork`/`ComposeUIComponents` convention of publishing everything as an Android library
  artifact with Robolectric available for tests that need it).
- `explicit-api = strict` is enabled for all non-test compilations — every public declaration needs
  an explicit visibility modifier and return type.

## Coverage And Testing Conventions

- `check` runs ktlint, detekt, Android lint, unit tests, and a JaCoCo line-coverage gate
  (minimum 70%) per module. Run `./gradlew check` (or a scoped `:module:check`) before considering
  a change done.
- Test stack matches Novalingo's own conventions: JUnit4 + MockK (prefer `mockk(relaxed = true)`
  plus `coEvery`/`coVerify`/`slot()`/`capture()` over hand-rolled fakes for interaction tests),
  Turbine for Flow assertions, `kotlinx-coroutines-test` (`runTest`, `backgroundScope`,
  `testScheduler.runCurrent()`/`advanceUntilIdle()` for scope-launched coroutines under test).
- Robolectric is available (`org.robolectric:robolectric`, `androidx.test.ext:junit`) for classes
  that touch `android.*` APIs (e.g. `SharedPreferences`, `Patterns`). **Known limitation**: in this
  AGP9/Kotlin toolchain combination, JaCoco does not measure line coverage for classes only
  exercised via `RobolectricTestRunner` — they read as 0% covered even when their tests pass. The
  known-affected classes (`TimberLogger`, `EmailValidationKt`, `LocalOverrideStore`) are excluded
  from the coverage ratio in `build-logic/convention/.../Jacoco.kt`'s `COVERAGE_EXCLUSIONS`. Keep
  writing real Robolectric tests for these classes regardless — the exclusion is about the metric,
  not about skipping verification. If you add a new Android-framework-dependent class, write its
  test the same way and add it to that exclusion list rather than lowering the module's coverage
  threshold.
- `SyncEngineTest` is the reference example for testing a generic contract: it defines its own
  `TestEntity`/`TestResourceAdapter`/`TestApi` fixtures rather than reusing app-specific types, and
  every test name documents the invariant it's protecting (ack guard, tombstone guard, pagination
  drain, etc.) — follow that pattern for new generic-engine tests.

## Publishing

- Version is sourced from `.release-please-manifest.json` (single source of truth); release-please
  owns bumping it via the release PR. Do not hand-edit the version elsewhere.
- `GROUP=io.github.maniramezan.kommon` and POM metadata live in `gradle.properties`.
- `com.vanniktech.maven-publish` + Dokka are applied automatically to every `com.android.library`
  module via the root `build.gradle.kts` `subprojects` block — new modules don't need to opt in
  manually, just add them to the `dokka(project(":..."))` list in the root `build.gradle.kts` so
  their KDoc is included in the aggregated site.
- CI/release workflow (`.github/workflows/ci.yml`, `release-please.yml`, `release.yml`,
  `docs.yml`) mirrors `kenwork`/`ComposeUIComponents` exactly. `release.yml` requires four repo
  secrets to actually publish to Maven Central: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
  `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_PASSWORD`.
- Remote: `https://github.com/maniramezan/kommon`.

## Build And Verification

```bash
./gradlew check                 # full check: tests, ktlint, detekt, lint, jacoco gate — all modules
./gradlew :sync:check           # scoped check for a single module
./gradlew ktlintFormat          # auto-fix formatting before committing
./gradlew dokkaGenerate         # aggregated KDoc site (build/dokka/html) — CI runs this too
./gradlew clean check           # verify from a clean state before a release
```

For dependency/build-logic changes, also run the affected module's `assemble`/`test` tasks and
inspect generated dependency or build failures before broadening scope.
