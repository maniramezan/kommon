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
