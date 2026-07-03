plugins {
    alias(libs.plugins.kommon.android.library)
}

android {
    namespace = "io.github.maniramezan.kommon.crashreporting"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
