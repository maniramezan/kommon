plugins {
    alias(libs.plugins.kommon.android.library)
}

android {
    namespace = "io.github.maniramezan.kommon.analytics"
}

dependencies {
    api(project(":foundation"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
