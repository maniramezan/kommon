plugins {
    alias(libs.plugins.kommon.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.maniramezan.kommon.debugui"
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":remoteconfig-debug"))
    api(project(":analytics-debug"))
    api(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
