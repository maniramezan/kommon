---
name: kommon-new-module
description: Checklist for adding a new Gradle module (artifact) to kommon — settings, convention plugin, Dokka aggregation, publishing, tests/coverage, fakes, and doc tables. Use when creating a module or splitting an existing one.
---

# Adding a kommon module

1. **Decide the boundary first.** A module is a published artifact consumers opt into. Split when a
   piece has a different dependency footprint (e.g. `-debug` modules that touch `android.content`,
   or anything pulling a new third-party library) or is independently useful. Don't split just to
   make files smaller.
2. **Create `<module>/build.gradle.kts`:**
   ```kotlin
   plugins {
       alias(libs.plugins.kommon.android.library)
   }

   android {
       namespace = "io.github.maniramezan.kommon.<package>"
   }

   dependencies {
       // api(...) for types that appear in this module's public signatures, implementation(...) otherwise.
       testImplementation(libs.junit)
       testImplementation(libs.kotlin.test.junit)
   }
   ```
   Never hardcode versions — add them to `gradle/libs.versions.toml` (check the latest stable first).
   No DI annotations, no Compose, no HTTP clients (see "Current Architecture Decisions" in `AGENTS.md`).
3. **Wire it up:**
   - `settings.gradle.kts` → `include(":<module>")`.
   - Root `build.gradle.kts` → `dokka(project(":<module>"))`. Publishing (vanniktech) and Dokka are
     applied automatically for `com.android.library` modules.
4. **Package:** `io.github.maniramezan.kommon.<module>` (sub-packages for `-debug` variants, e.g.
   `...remoteconfig.debug`).
5. **Tests + coverage:** the JaCoCo gate (70% line) runs as part of `check`. Classes that can only be
   tested under Robolectric go into `COVERAGE_EXCLUSIONS` in
   `build-logic/convention/src/main/kotlin/Jacoco.kt` — still write their Robolectric tests. Prefer a
   pure-Kotlin implementation over `android.*` where possible so the class needs no exclusion.
6. **Fakes:** for every new port (interface consumers implement or inject), add an in-memory fake to
   `:testing` and make `:testing` depend on the new module with `api(project(":<module>"))`.
7. **Docs (same PR):**
   - `AGENTS.md` → "Repository Map" entry with its dependencies.
   - `README.md` → module table row + a short usage snippet.
   - KDoc on every public declaration (`explicit-api = strict` forces visibility; KDoc is on you).
8. **Verify** with the `kommon-verify` skill.
