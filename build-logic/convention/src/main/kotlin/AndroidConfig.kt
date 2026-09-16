import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 26

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) {
    extension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig {
            minSdk = MIN_SDK
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        buildTypes {
            getByName("debug") {
                enableUnitTestCoverage = true
            }
        }

        // Robolectric-driven unit tests need Android resources on the test classpath;
        // returning default values keeps un-shadowed Android stubs (e.g. android.util.Log)
        // from throwing in plain JVM unit tests.
        testOptions {
            targetSdk = TARGET_SDK
            unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
                all {
                    // Required by Robolectric 4.17+ on JDK 17+: stronger module encapsulation
                    // blocks the JDK internals it reaches into unless these are opened.
                    it.jvmArgs(
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "--add-opens=java.base/java.util=ALL-UNNAMED",
                        "--add-opens=java.base/java.io=ALL-UNNAMED",
                        "--add-opens=java.base/java.net=ALL-UNNAMED",
                        "--add-opens=java.base/java.security=ALL-UNNAMED",
                        "--add-opens=java.base/java.text=ALL-UNNAMED",
                        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                    )
                }
            }
        }
    }
}
