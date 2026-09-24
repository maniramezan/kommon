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
| `foundation` | `BaseViewModel`, `LoadingState`/`LoadingError`, email validation, and the logging seam. |
| `sync` | Offline delta-sync engine with pagination, acknowledgements, and tombstones. |
| `authsession` | Provider-agnostic auth and cold-start session contracts. |
| `analytics-core` / `analytics-debug` | Analytics port, payload sanitizer, and bounded debug capture. |
| `crashreporting` / `telemetry` | Crash-reporting port; OpenTelemetry-shaped spans with a `trace {}` helper. |
| `remoteconfig` / `remoteconfig-debug` | Typed, validated config keys, a shared resolver, and debug-only local overrides. |
| `parsing` | Localized-value and JSON string-array parsers. |
| `design-system` | Kotlin Multiplatform design tokens (colors, spacing, shapes, typography, motion). |
| `testing` | Consumer test fakes for sync, auth, analytics, logging, and remote config. |

## Installation

```kotlin
dependencies {
    implementation("io.github.maniramezan.kommon:sync:<version>")
    testImplementation("io.github.maniramezan.kommon:testing:<version>")
}
```

Artifacts are published independently. Use only the modules your app needs.

## Usage

### Sync

Implement `SyncResourceAdapter` around one local resource and its API DTOs, then invoke the
engine from your app's sync coordinator:

```kotlin
val engine = SyncEngine(cursorStore = roomCursorStore, telemetry = analyticsSyncTelemetry)

engine.sync(tasksAdapter)                              // one resource
engine.syncAll(listOf(tasksAdapter, decksAdapter))     // isolated: one failure doesn't stop the rest
    .onFailure { error -> logger.warning("Sync", "sync failed", error) }
```

`syncKey` must be stable and exactly match both the server's `applied[].key` and every server
change key. Make adapter writes and cursor persistence transactional in the consumer app where
the storage layer permits it. Keep tombstones until the server acknowledges deletion; clean-up
policy belongs to the consuming app. The engine serializes calls made through one instance, and
cancelling the calling coroutine cancels the pass (it is never reported as a sync failure). A
server that answers `hasMore = true` without advancing its cursor fails the pass instead of
looping forever.

### Foundation

```kotlin
data class LessonsState(val lessons: LoadingState<List<Lesson>> = LoadingState.Idle)

class LessonsViewModel(private val repo: LessonRepository) : BaseViewModel<LessonsState, LessonsEvent>(LessonsState()) {
    override fun onEvent(event: LessonsEvent) {
        when (event) {
            LessonsEvent.Load -> viewModelScope.launch {
                updateState { copy(lessons = LoadingState.Loading) }
                val lessons = loadingStateOf { repo.fetchLessons() }   // Loaded, or Failed(LoadingError)
                updateState { copy(lessons = lessons) }
            }
        }
    }
}

// Logging: plant once at startup, then inject the logger wherever a KommonLogger is needed.
TimberLogger.setup(isDebug = BuildConfig.DEBUG) { _, tag, message, error ->
    crashlytics.log("$tag: $message")
    error?.let(crashlytics::recordError)
}
val logger: KommonLogger = TimberLogger(tagPrefix = "MyApp")
```

### Auth session

```kotlin
val initializer = AuthSessionInitializer(authRepository, tokenProvider, sessionStore, logger, appScope)
initializer.start()   // warms the token before the first request, then tracks sign-in state

val message = error.authSignInErrorMessage(providerName = "Google")
```

### Analytics and telemetry

```kotlin
val analytics: AnalyticsClient =
    CompositeAnalyticsClient(listOf(firebaseAnalytics, amplitudeAnalytics), logger)   // failures isolated per client

// Debug builds: capture every call for an in-app "recent events" screen.
val debugStore = InMemoryAnalyticsDebugStore(capacity = 200)
val debugAnalytics = AnalyticsCaptureClient(delegate = analytics, debugStore = debugStore)

// Spans: time a block, recording OK / ERROR (with error.type) automatically.
val otel = AnalyticsOpenTelemetryClient(analytics)
val words = otel.trace("fetch_words", SpanKind.CLIENT, mapOf("deck" to deckId)) { api.fetchWords(deckId) }
```

### Remote config

```kotlin
object AppConfigKeys {
    val NEW_ONBOARDING = ConfigKey.bool("new_onboarding", "Show the redesigned onboarding", default = false)
    val THEME = ConfigKey.string("theme", "UI theme", default = "system", allowedValues = listOf("light", "dark", "system"))
    val ALL_KEYS = listOf(NEW_ONBOARDING, THEME)
}

class FirebaseConfigClient(
    private val firebase: FirebaseRemoteConfig,
    overrides: LocalOverrideStore?,                     // remoteconfig-debug; pass null in release
) : RemoteConfigClient {
    private val resolver = ConfigResolver(AppConfigKeys.ALL_KEYS, overrides) { key -> firebase.readTyped(key) }

    override suspend fun fetchAndActivate() { firebase.fetchAndActivate().await() }
    override fun value(key: ConfigKey): ConfigValue = resolver.resolve(key).value   // override -> remote -> default
    override fun allValues(): List<ResolvedConfigEntry> = resolver.resolveAll()
}
```

`ConfigKey` validates itself at construction (the default must match the declared type and any
`allowedValues`), so a mistyped key fails at app start rather than at the call site.

### Parsing

```kotlin
LocalizedValueParser().parse("""{"en_us":"Hello","fr":"Bonjour"}""")   // "Hello"
LocalizedValueParser(listOf("fr")).parse("""{"en_us":"Hello","fr":"Bonjour"}""")   // "Bonjour"
JsonEncodedStringArrayParser.parseOrPassthrough("""["one","two"]""")   // "one\ntwo"
```

### Testing

```kotlin
val analytics = RecordingAnalyticsClient()
val auth = FakeAuthRepository().apply { nextFailure = IOException("offline") }
val config = FakeRemoteConfigClient(keys = AppConfigKeys.ALL_KEYS).apply { set(AppConfigKeys.NEW_ONBOARDING, ConfigValue.Bool(true)) }
val logger = RecordingLogger()

// ...exercise the subject...
assertEquals(listOf("onboarding_started"), analytics.trackedEventNames)
assertTrue(logger.entriesAt(RecordingLogger.Level.ERROR).isEmpty())
```

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

Contributor and AI-agent guidance lives in [`AGENTS.md`](AGENTS.md); repo-specific workflows
(adding a module, extracting code from an app, changing the sync engine, verifying changes) are
packaged as skills under [`.claude/skills/`](.claude/skills).
