#!/usr/bin/env bash
# JVM-only fallback verification for kommon when AGP can't be resolved (no dl.google.com access).
#
# Compiles every Android-library module's main + test sources as ONE plain Kotlin/JVM project
# against compile-only Android stubs, then runs the non-Robolectric unit tests, ktlint, and detekt
# with the repo's detekt config. It does NOT run Android lint, Robolectric tests, JaCoCo, or the
# :design-system KMP build — say so when reporting results.
#
# Usage: skills/kommon-verify/jvm-verify.sh [workdir]   (needs a `gradle` 8.14+ on PATH)
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
skill="$repo/skills/kommon-verify"
work="${1:-${TMPDIR:-/tmp}/kommon-jvm-verify}"
catalog="$repo/gradle/libs.versions.toml"
ver() { sed -n "s/^$1 = \"\(.*\)\"/\1/p" "$catalog"; }

mkdir -p "$work/stubs"
cp "$skill"/stubs/*.kt "$work/stubs/"

# Timber is an AAR on Maven Central; extract its classes.jar so TimberLogger compiles.
if [ ! -f "$work/timber.jar" ]; then
  path="com/jakewharton/timber/timber/$(ver timber)/timber-$(ver timber).aar"
  for mirror in https://plugins.gradle.org/m2 https://repo.maven.apache.org/maven2 https://repo1.maven.org/maven2; do
    curl -fsSL --retry 3 --retry-delay 10 --retry-all-errors -o "$work/timber.aar" "$mirror/$path" && break
  done
  unzip -o -q -j "$work/timber.aar" classes.jar -d "$work" && mv "$work/classes.jar" "$work/timber.jar"
fi

# Every module that applies kommon.android.library (i.e. everything but the KMP design-system).
modules=$(grep -l 'kommon.android.library' "$repo"/*/build.gradle.kts | xargs -n1 dirname | xargs -n1 basename | sort)
mods_kts=$(printf '"%s", ' $modules)

cat > "$work/settings.gradle.kts" <<EOF
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://plugins.gradle.org/m2/")
        mavenCentral()
    }
}
rootProject.name = "kommon-jvm-verify"
EOF

cat > "$work/build.gradle.kts" <<EOF
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "$(ver kotlin)"
    kotlin("plugin.serialization") version "$(ver kotlin)"
    id("io.gitlab.arturbosch.detekt") version "$(ver detekt)"
    id("org.jlleitschuh.gradle.ktlint") version "$(ver ktlint)"
}

val repo = "$repo"
val mods = listOf($mods_kts)

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") }
}

sourceSets {
    create("stubs") { kotlin.srcDir("stubs") }
    main {
        kotlin.srcDirs(mods.map { "\$repo/\$it/src/main/kotlin" })
        compileClasspath += sourceSets["stubs"].output
        runtimeClasspath += sourceSets["stubs"].output
    }
    test {
        kotlin.srcDirs(mods.map { "\$repo/\$it/src/test/kotlin" })
        compileClasspath += sourceSets["stubs"].output
        runtimeClasspath += sourceSets["stubs"].output
        // Robolectric-only tests need the real Android runtime.
        kotlin.exclude { spec -> !spec.isDirectory && spec.file.readText().contains("RobolectricTestRunner") }
    }
}

tasks.named<KotlinCompile>("compileStubsKotlin") { compilerOptions.freeCompilerArgs.add("-Xexplicit-api=disable") }
tasks.named<KotlinCompile>("compileTestKotlin") { compilerOptions.freeCompilerArgs.add("-Xexplicit-api=disable") }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$(ver coroutines)")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$(ver coroutines)")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$(ver kotlinxSerialization)")
    implementation("io.mockk:mockk:$(ver mockk)")
    implementation("app.cash.turbine:turbine:$(ver turbine)")
    implementation("junit:junit:$(ver junit)")
    implementation(files("timber.jar"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$(ver kotlin)")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("\$repo/config/detekt/detekt.yml")
    source.setFrom(mods.flatMap { listOf("\$repo/\$it/src/main/kotlin", "\$repo/\$it/src/test/kotlin") })
}

ktlint { filter { exclude { it.file.path.startsWith(projectDir.path) } } }
EOF

cd "$work"
# Maven Central rate-limits bursts (HTTP 429) in some sandboxes; downloads are cached, so retry.
for attempt in 1 2 3 4 5 6; do
  if gradle test ktlintCheck detekt --continue --console=plain --max-workers=1 > verify.log 2>&1; then
    grep -E "BUILD" verify.log; exit 0
  fi
  grep -q " 429 " verify.log || break
  sleep $((attempt * 15))
done
grep -E "^e:|kommon/|FAILED|BUILD" verify.log | head -80
exit 1
