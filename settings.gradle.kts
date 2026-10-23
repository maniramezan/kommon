pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// :debug-ui renders with KMPComponents, which itself depends on :design-system. Pass
// -PuseLocalKmpComponents=true to build against a sibling checkout (or -PlocalKmpComponentsDir=<path>)
// instead of the published release, e.g. before a new KMPComponents version reaches Maven Central.
if (providers.gradleProperty("useLocalKmpComponents").orNull.toBoolean()) {
    val localKmpComponents = file(providers.gradleProperty("localKmpComponentsDir").orElse("../KMPComponents").get())
    require(localKmpComponents.resolve("settings.gradle.kts").isFile) {
        "useLocalKmpComponents requires a KMPComponents checkout at $localKmpComponents"
    }
    includeBuild(localKmpComponents)
}

rootProject.name = "kommon"

include(":foundation")
include(":sync")
include(":authsession")
include(":analytics-core")
include(":analytics-debug")
include(":crashreporting")
include(":telemetry")
include(":remoteconfig")
include(":remoteconfig-debug")
include(":parsing")
include(":testing")
include(":design-system")
include(":debug-ui")
