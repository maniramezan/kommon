---
name: kommon-extract-component
description: How to port a component out of a product app (e.g. Novalingo) into kommon — deciding what is generic, splitting contract/algorithm from app-specific adapters, removing DI/logging/Android coupling, and writing generic fixtures. Use when moving any app code into this repo.
---

# Extracting a component into kommon

kommon keeps **generic contracts and algorithms**; the consuming app keeps **domain models,
concrete DTOs, Room entities/DAOs, provider SDK calls, and DI wiring**.

## 1. Draw the line

For each type in the candidate code, ask: would a second, unrelated app use this unchanged?

| Keep in kommon | Leave in the app |
| --- | --- |
| Engine/loop/algorithm (`SyncEngine`) | Concrete `SyncResourceAdapter` for `Task`, `Deck`, ... |
| Port interfaces (`AnalyticsClient`, `RemoteConfigClient`) | Firebase/Amplitude/Supabase implementations |
| Wire envelopes with generic payloads (`SyncResponse<C>`) | The payload DTOs |
| Value types + validation (`ConfigKey`) | The app's key registry (`AppConfigKeys.ALL_KEYS`) |
| Pass-through extension points (`metadataExtra`) | Product fields read out of them (quota tiers) |

If a generic type needs a product-specific field, add an opaque pass-through
(`Map<String, String>`) or a type parameter instead of the field.

## 2. Decouple

- Remove `@Inject`/`@Singleton`/Hilt modules → plain constructor parameters with sensible defaults.
- Replace Timber/`android.util.Log` → a `logger: KommonLogger = NoOpLogger` parameter.
- Replace `System.currentTimeMillis()` → `clockMs: () -> Long = System::currentTimeMillis`.
- Replace `android.*` utilities with pure Kotlin where behavior allows (see `isValidEmail()`); if
  the Android API is essential, put the class in a `-debug`/Android-specific module.
- Rename product vocabulary out of public names and KDoc (keep a "ported from Novalingo's X" note
  if it helps reviewers).
- Keep `CancellationException` transparent in any catch-all (see `AGENTS.md` conventions).

## 3. Test generically

Follow `SyncEngineTest`: define local fixtures (`TestEntity`, `TestResourceAdapter`, `TestApi`)
instead of importing app types, mock only the outermost boundary, and name every test after the
invariant it protects. Port the app's existing tests first, then add tests for anything the
extraction changed.

## 4. Land it

- Use the `kommon-new-module` skill if it needs a new artifact.
- Add a fake to `:testing` for every new port.
- In the PR body, record where the line was drawn and what stays in the app — this is the worked
  example future extractions will follow.
- Then open the follow-up in the app that deletes its copy and depends on the kommon artifact.
