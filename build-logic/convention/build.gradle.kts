plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.binary.compatibility.validator)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "kommon.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
