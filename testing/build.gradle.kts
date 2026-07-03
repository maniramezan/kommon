plugins {
    alias(libs.plugins.kommon.android.library)
}

android {
    namespace = "io.github.maniramezan.kommon.testing"
}

dependencies {
    // Test utilities are meant to be on a consumer's test classpath, so expose deps as api.
    api(project(":sync"))
    api(project(":analytics-core"))
    api(project(":remoteconfig"))
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
    api(libs.turbine)
    api(libs.junit)

    testImplementation(libs.kotlin.test.junit)
}
