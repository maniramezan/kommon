plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
}

// Single source of truth for the published version: the release workflow injects VERSION_NAME
// from the computed SemVer tag. Local and CI builds without a release tag use a snapshot version.
val libraryVersion: String =
    providers.gradleProperty("VERSION_NAME").orElse("0.0.0-SNAPSHOT").get()

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = libraryVersion
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

// Aggregate the published modules' KDoc into a single Dokka HTML site (build/dokka/html)
// via `./gradlew dokkaGenerate`.
dependencies {
    dokka(project(":foundation"))
    dokka(project(":sync"))
    dokka(project(":authsession"))
    dokka(project(":analytics-core"))
    dokka(project(":analytics-debug"))
    dokka(project(":crashreporting"))
    dokka(project(":telemetry"))
    dokka(project(":remoteconfig"))
    dokka(project(":remoteconfig-debug"))
    dokka(project(":parsing"))
    dokka(project(":testing"))
}

subprojects {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
    }

    // Every published Android library gets API docs (Dokka) and Maven Central
    // publishing (vanniktech, configured from the root gradle.properties POM_* keys).
    pluginManager.withPlugin("com.android.library") {
        pluginManager.apply("org.jetbrains.dokka")
        pluginManager.apply("com.vanniktech.maven.publish")
    }
}
