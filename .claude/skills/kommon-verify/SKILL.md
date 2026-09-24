---
name: kommon-verify
description: Verify a kommon change before committing — which Gradle tasks to run, and a JVM-only fallback (jvm-verify.sh) for sandboxes where AGP can't be downloaded from Google Maven (dl.google.com blocked / 403) or Maven Central rate-limits (429). Use before every commit/PR in this repo.
---

# Verifying a kommon change

## Normal path (network can reach Google Maven)

```bash
./gradlew ktlintFormat                 # auto-fix formatting first
./gradlew :<module>:check              # fast loop on the module you touched
./gradlew check                        # everything: tests, ktlint, detekt, Android lint, JaCoCo 70% gate
./gradlew dokkaGenerate                # CI runs this too; catches broken KDoc links
```

Also run `:<module>:check` for every module that depends on the one you changed (see the module
map in `AGENTS.md`; `:testing` depends on almost everything).

## Fallback: AGP unavailable

Symptom: `./gradlew` fails at settings/build-logic with `Could not resolve ... dl.google.com ... 403`
(policy-blocked host) or repeated `429 Too Many Requests` from Maven Central.

Run:

```bash
.claude/skills/kommon-verify/jvm-verify.sh [scratch-dir]   # needs `gradle` 8.14+ on PATH
```

It builds all `kommon.android.library` modules' sources as a single Kotlin/JVM project against
compile-only Android stubs (`stubs/`), with versions read from `gradle/libs.versions.toml`, then runs:

- the Kotlin compile of every module's main sources with `-Xexplicit-api=strict`,
- every unit test that does **not** use `RobolectricTestRunner`,
- ktlint (main + test) and detekt with `config/detekt/detekt.yml`.

It retries on 429 because downloads are cached between attempts.

**It does not cover** Android lint, Robolectric tests (`TimberLoggerTest`, `LocalOverrideStoreTest`),
the JaCoCo gate, Dokka, or the `:design-system` KMP build. When you report results, list these as
"not run locally; covered by CI" rather than claiming a full `check` pass.

If you add a new Android API to main sources, add a minimal compile-only stub for it under
`stubs/` (only the members you call).

## Before pushing

- Re-read the diff for: swallowed `CancellationException`, new `public` API without KDoc, a new
  port without a matching fake in `:testing`, and module map/README drift.
- Breaking public API changes are allowed pre-1.0 but must be called out in the commit/PR body
  (use `feat!:` / `BREAKING CHANGE:` so `release.yml` bumps the minor version).
