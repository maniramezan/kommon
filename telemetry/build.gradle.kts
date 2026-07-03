plugins {
    alias(libs.plugins.kommon.android.library)
}

android {
    namespace = "io.github.maniramezan.kommon.telemetry"
}

dependencies {
    api(project(":analytics-core"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
}
