# kommon Agent Instructions

## Purpose

This repo owns a reusable, Hilt-free, Kotlin-first Android/JVM helper library extracted from
production apps (initially Novalingo). Keep the library product-agnostic and reusable across
projects: generic contracts and algorithms live here; app-specific domain models, concrete DTOs,
Room entities/DAOs, and DI wiring stay in the consuming app.

`AGENTS.md` is the source of truth for agent guidance. Keep `CLAUDE.md` thin and point back to
this file.

## Required Skill Loading

Repo-local skills live in `.claude/skills/` and encode this repo's own workflows — load the
matching one first:

- `kommon-new-module` — adding a module (settings, Dokka list, README/AGENTS tables, fakes).
- `kommon-extract-component` — porting code out of a product app (Novalingo) into `kommon`.
- `kommon-sync-engine` — any change to `:sync` (invariant checklist + test pattern).
- `kommon-verify` — how to verify a change, including when Google Maven is unreachable.

Also load the best matching installed skill before non-trivial planning, implementation, or review
work:

- `android-gradle-logic` for Gradle convention plugins, version catalogs, and module wiring.
- `gradle-build-performance` for slow Gradle builds, CI time, or dependency graph cost.
- `kotlin-backend-jpa-entity-mapping` if a future module touches JPA/Room entity design.
- `kotlin-tooling-java-to-kotlin` if porting more Java-era Novalingo code into this repo.

When adding or updating dependencies, check the latest stable version online first when network
access is available, then pin the verified version in `gradle/libs.versions.toml`.

## Repository Map

- `:foundation` — `BaseViewModel`, `LoadingState` (+ `map`, `loadingStateOf {}`), `LoadingError`
  (throwable → user-facing message classifier), the `KommonLogger` logging seam (+ `TimberLogger`
  bridge with an optional `ReleaseLogSink`), and a pure-Kotlin `isValidEmail()`. No other module
  dependencies.
- `:sync` — the generic delta-sync engine: `SyncEngine`, `SyncResourceAdapter`, `SyncableEntity`/
  `SyncState`, the `SyncRequest`/`SyncResponse` wire envelope, `SyncCursorStore`, `SyncTelemetrySink`.
  Depends on `:foundation` only. This is the highest-value, most-tested module — treat its
  invariants (ack guard, pending guard, tombstone reconciliation, pagination drain + non-advancing
  cursor guard, per-resource isolation, cancellation transparency) as load-bearing; see the KDoc on
  `SyncEngine` and `SyncEngineTest` before changing it. Load the `kommon-sync-engine` skill first.
- `:authsession` — `AuthRepository` (composing `AuthStateProvider`, `AnonymousAuth`,
  `EmailPasswordAuth`, `SocialAuth`), `AuthUser`, `AuthSessionStore`, `AuthSessionInitializer`,
  `authSignInErrorMessage()`. Depends on `:foundation` for the logging seam.
- `:analytics-core` — `AnalyticsClient` port, `AnalyticsEvent`, `NoOp`/`Logging`/
  `CompositeAnalyticsClient` (per-client failure isolation), `AnalyticsPayloadSanitizer`. Depends on
  `:foundation`.
- `:analytics-debug` — `AnalyticsDebugStore`, `AnalyticsCaptureClient` decorator. Depends on
  `:analytics-core`.
- `:crashreporting` — `CrashlyticsClient` port, `NoOpCrashlyticsClient`, `RecordingCrashlyticsClient`
  (a test double shipped in `main`, not `test`, so consumer apps can use it directly). No deps.
- `:telemetry` — `OpenTelemetryClient` port, `SpanKind`/`SpanStatus` constants, the `trace {}`
  timing helper, and the `AnalyticsOpenTelemetryClient` bridge. Depends on `:analytics-core`.
- `:remoteconfig` — `RemoteConfigClient` port, `ConfigKey` (validated; `ConfigKey.bool/string/int/
  double` factories), `ConfigValue`/`ConfigValueType`, `ResolvedConfigEntry`/`ValueSource`, and
  `ConfigResolver` + `ConfigOverrideSource` (the override → remote → default resolution every
  provider bridge should reuse). Deliberately carries **no** built-in key registry — apps own their
  own `object AppConfigKeys { val ALL_KEYS = ... }`.
- `:remoteconfig-debug` — `LocalOverrideStore` (SharedPreferences-backed debug override store; a
  `ConfigOverrideSource`). Depends on `:remoteconfig` and `:foundation`.
- `:parsing` — `LocalizedValueParser`, `JsonEncodedStringArrayParser`. No deps beyond
  kotlinx-serialization.
- `:testing` — fakes for consumer unit tests (`FakeSyncCursorStore`, `RecordingAnalyticsClient`,
  `FakeRemoteConfigClient`, `RecordingLogger`, `FakeAuthRepository`, `FakeAuthTokenProvider`,
  `InMemoryAuthSessionStore`). Depends on `:foundation`, `:sync`, `:authsession`,
  `:analytics-core`, `:remoteconfig` as `api` so consumers get them transitively on their test
  classpath. When you add a port to a module, add a matching fake here.
- `:design-system` — Kotlin Multiplatform (Android, JVM, iOS) platform-neutral design tokens
  (`ColorToken`, spacing/shape/typography/motion tokens, `ThemeTokens`, `KommonDesignTokens`). No
  Compose types; renderers live in `KMPComponents`. Uses the KMP Android plugin directly rather
  than `kommon.android.library`, so it is not covered by the JaCoCo gate.
- `build-logic/` — Gradle convention plugin (`kommon.android.library`).
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
- **No networking or rendered UI here**: HTTP transport belongs in `kenwork`
  (`io.github.maniramezan.kenwork:*`). Platform-neutral design-system values and contracts live in
  `:design-system`; Compose adapters and rendered components live in `KMPComponents`. Keep `kommon`
  free of Compose types (`Color`, `Dp`, `TextStyle`, and composables) so Android, desktop, and iOS
  consumers can interpret the same tokens without taking a UI dependency.
- The library is pre-1.0 (`0.1.0` and climbing); breaking API changes are still acceptable but
  should be called out explicitly, since Novalingo is the intended first consumer.

## Build Logic And Dependency Shape

- Shared Gradle behavior lives in `build-logic/convention` (`AndroidLibraryConventionPlugin`,
  `Jacoco.kt`). Reuse those conventions instead of duplicating
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
  known-affected classes (`TimberLogger`, `LocalOverrideStore`) are excluded
  from the coverage ratio in `build-logic/convention/.../Jacoco.kt`'s `COVERAGE_EXCLUSIONS`. Keep
  writing real Robolectric tests for these classes regardless — the exclusion is about the metric,
  not about skipping verification. If you add a new Android-framework-dependent class, write its
  test the same way and add it to that exclusion list rather than lowering the module's coverage
  threshold.
- Prefer pure-Kotlin implementations over `android.*` APIs when behavior allows it (e.g.
  `isValidEmail()` reimplements `Patterns.EMAIL_ADDRESS`): consumers' plain JVM unit tests run with
  `isReturnDefaultValues = true`, where Android stubs silently return `null`/`0`.
- `SyncEngineTest` is the reference example for testing a generic contract: it defines its own
  `TestEntity`/`TestResourceAdapter`/`TestApi` fixtures rather than reusing app-specific types, and
  every test name documents the invariant it's protecting (ack guard, tombstone guard, pagination
  drain, etc.) — follow that pattern for new generic-engine tests.

## Publishing

- Version is sourced from the release workflow's computed bare SemVer tag. Release automation
  injects `VERSION_NAME` during publish; do not hardcode or hand-edit a release version elsewhere.
- `GROUP=io.github.maniramezan.kommon` and POM metadata live in `gradle.properties`.
- `com.vanniktech.maven-publish` + Dokka are applied automatically to every `com.android.library`
  module via the root `build.gradle.kts` `subprojects` block — new modules don't need to opt in
  manually, just add them to the `dokka(project(":..."))` list in the root `build.gradle.kts` so
  their KDoc is included in the aggregated site.
- CI/release workflow (`.github/workflows/ci.yml`, `release.yml`, `docs.yml`) mirrors the direct
  post-CI auto-release setup used in sibling repos. `release.yml` requires four repo secrets to
  actually publish to Maven Central: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
  `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_PASSWORD`.
- Remote: `https://github.com/maniramezan/kommon`.

## Library Code Conventions

- **Coroutine cancellation is never swallowed.** Anything that catches `Throwable` or uses
  `runCatching` around suspending work must rethrow `CancellationException` first (see
  `SyncEngine.runCatchingNonCancellation`, `AuthSessionInitializer.runContained`,
  `loadingStateOf`).
- **Background work must not crash the host.** Long-lived coroutines launched into a
  library-owned or caller-provided scope contain and log failures via `KommonLogger`.
- **Observational side channels are isolated.** Telemetry, analytics fan-out, and logging must
  never change the outcome of the primary operation (`SyncEngine.reportTelemetry`,
  `CompositeAnalyticsClient`).
- **Inject time.** Take a `clockMs: () -> Long = System::currentTimeMillis` parameter instead of
  calling the clock directly, so tests are deterministic.
- **Validate at construction.** Value types that can be mis-declared (`ConfigKey`,
  `ColorToken`) `require(...)` their invariants in `init`, so misconfiguration fails at startup.
- One public type per file, named after the file, unless the types are a tiny sealed family.

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

If `./gradlew` cannot resolve AGP (sandboxed agents without access to `dl.google.com`), follow the
`kommon-verify` skill's JVM-only fallback and state in the PR which checks could not run locally.
