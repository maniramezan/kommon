plugins {
    alias(libs.plugins.kommon.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.maniramezan.kommon.sync"
}

dependencies {
    api(project(":foundation"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
