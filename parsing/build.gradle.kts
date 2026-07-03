plugins {
    alias(libs.plugins.kommon.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.maniramezan.kommon.parsing"
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
