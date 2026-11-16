# kommon

Reusable Kotlin-first Android building blocks extracted from production apps. `kommon` is
provider- and DI-framework-agnostic: consumers bridge its contracts to their own persistence,
authentication, analytics, remote-config, and telemetry SDKs.

## Requirements

- Android library consumers using JDK 17 or newer.
- Kotlin 2.4.x and Android Gradle Plugin 9.4.x are used to build this release.

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
| `speech` | On-device speech recognition and synthesis over Android's `SpeechRecognizer` / `TextToSpeech`, plus word-level transcript alignment. |
| `design-system` | Kotlin Multiplatform design tokens (colors, spacing, shapes, typography, motion) and WCAG contrast helpers. |
| `debug-ui` | Compose debug screens for remote-config overrides and captured analytics, using Compose Material 3. |
| `testing` | Consumer test fakes for sync, auth, analytics, logging, remote config, and speech. |

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
looping forever. A paginated full snapshot only persists its cursor after the last page is
applied, so an interrupted snapshot restarts rather than resuming without reconciliation.
Override `upsertAll` to write each page of changes in one database transaction. Repeated changes
for a key are merged in response order before the final row is written. The engine reads rows
before invoking `upsertAll`; a transaction around that batch alone cannot prevent concurrent local
edits from being overwritten. Adapters must enforce conditional writes against the current local
version when local editing can race sync persistence.

`limit` controls response page size. Existing calls send all pending rows in one push. When
the server supports independent partial pushes and retries by stable row key, opt into bounded
requests with a separate `pushBatchSize`:

```kotlin
engine.sync(tasksAdapter, limit = 100, pushBatchSize = 50)
engine.syncAll(listOf(tasksAdapter, decksAdapter), pushBatchSize = 50)
```

The bound counts upserts and deletes together. Each batch's acknowledgements, response pages,
and snapshot reconciliation finish before the next batch uses the saved cursor. A later failure
keeps earlier batches committed and leaves unacknowledged rows pending for retry. Pending rows
are snapshotted once per pass; edits made during sync remain pending for the next pass, and
`pending()` still loads all pending rows into memory. An empty backlog still pulls once.
Existing method signatures and wire fields are preserved; batching remains opt-in so consumers
can confirm their server's partial-push semantics before enabling it.

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

### Debug screens

`debug-ui` renders the debug modules with Compose Material 3. Add it with
`debugImplementation("io.github.maniramezan.kommon:debug-ui:<version>")`, wrap the screens in
`MaterialTheme`, and keep
them behind a debug-only entry point:

```kotlin
MaterialTheme {
    RemoteConfigDebugScreen(client = configClient, overrideStore = localOverrideStore)
}
MaterialTheme {
    AnalyticsDebugScreen(store = analyticsDebugStore)
}
```

Both screens also have stateless overloads that take the entries and callbacks directly, and every
string is a parameter (`RemoteConfigDebugLabels`, `AnalyticsDebugLabels`) for localization.

### Parsing

```kotlin
LocalizedValueParser().parse("""{"en_us":"Hello","fr":"Bonjour"}""")   // "Hello"
LocalizedValueParser(listOf("fr")).parse("""{"en_us":"Hello","fr":"Bonjour"}""")   // "Bonjour"
JsonEncodedStringArrayParser.parseOrPassthrough("""["one","two"]""")   // "one\ntwo"
```

### Speech

`AndroidSpeechRecognitionSession` and `AndroidSpeechSynthesisSession` implement the
`SpeechRecognizing` / `SpeechSynthesizing` ports. The host app owns the `RECORD_AUDIO` permission.

Speech is local-only by default. Recognition uses only the on-device recognizer
(`requiresOnDevice = true`) and fails with `Reason.NotAvailable` if there is none (check
`isOnDeviceRecognitionAvailable(context)` first); synthesis uses only voices that work offline
(`allowsNetworkVoices = false`) and fails if the language has no local voice. Opting out
(`requiresOnDevice = false`, `allowsNetworkVoices = true`) may send audio or text to remote servers.

```kotlin
val recognizer: SpeechRecognizing = AndroidSpeechRecognitionSession(context)
val configuration = SpeechRecognitionConfiguration(locale = Locale.US)

recognizer.recognize(configuration).collect { event ->
    when (event) {
        is SpeechRecognitionEvent.Transcript -> show(event.transcript.text)
        is SpeechRecognitionEvent.AudioLevel -> meter(event.level)
        is SpeechRecognitionEvent.Ready, SpeechRecognitionEvent.SilenceDetected -> Unit
    }
}

val alignment = TranscriptAlignment.align(
    reference = "The quick brown fox",
    heard = transcript.words.map { it.transcriptToken },
)
alignment.score
```

`SpeechModelAssets.status(context, locale)` reports whether the on-device model is installed and
`requestDownload` triggers the download (Android 13+). Per-word confidence and timing need Android 14.

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

Every Android module's `check` runs `apiCheck` against a committed `api/<module>.api`
baseline generated from the release AAR's `classes.jar`. This checks public Kotlin and Java
signatures without depending on AGP's built-in Kotlin being detected by the validator plugin.
Empty dumps fail verification. Android resources, manifest entries, and dependency exposure
are outside this signature check.

For an intentional API change, run `./gradlew :<module>:apiDump` and review and commit the
baseline diff with the implementation. `:design-system` uses Kotlin's multiplatform
`checkKotlinAbi` and `updateKotlinAbi` tasks instead. Explain breaking changes and migration
steps in the PR description.

Contributor and AI-agent guidance lives in [`AGENTS.md`](AGENTS.md); repo-specific workflows
(adding a module, extracting code from an app, changing the sync engine, verifying changes) are
packaged as skills under [`skills/`](skills/).

### Release automation

Release runs after successful push CI on `main`. The release GitHub App uses the existing
`RELEASE_PLEASE_APP_ID` and `RELEASE_PLEASE_APP_PRIVATE_KEY` environment secrets and requires
Contents write and Workflows write permissions. Approve those permissions on the app installation
before merging workflow changes. Token creation runs before Maven publishing.

If Maven publishing completes but GitHub tagging fails, recover the tag at the exact source commit
from that publishing run and create its GitHub Release. A Maven version cannot be overwritten;
rerunning publication for the same version will fail. Version `0.2.0` was recovered at
`b7f83d6142a41ba4a7b6e6304dddf8e5778ff93c`, the commit used by its September 24 publishing run.
