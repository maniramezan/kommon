plugins {
    alias(libs.plugins.kommon.android.library)
}

android {
    namespace = "io.github.maniramezan.kommon.speech"
}

dependencies {
    // KommonLogger and the coroutines Flow type appear in this module's public signatures.
    api(project(":foundation"))
    api(libs.kotlinx.coroutines.core)

    // Dispatchers.Main for driving SpeechRecognizer / TextToSpeech on the main thread.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
