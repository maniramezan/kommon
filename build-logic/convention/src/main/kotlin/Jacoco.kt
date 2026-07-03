import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

private val COVERAGE_EXCLUSIONS =
    listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*\$\$serializer.class",
        // Robolectric's sandboxed classloader isn't instrumented by JaCoco's offline agent in
        // this AGP/Kotlin toolchain combination, so classes that only exercise android.* APIs
        // (and are therefore only testable under Robolectric) read as 0% covered even though
        // their behavior is exercised by real, passing tests. Exclude them from the ratio rather
        // than chase an unmeasurable number; this does not exempt them from having tests.
        "**/TimberLogger*.class",
        "**/EmailValidationKt*.class",
        "**/LocalOverrideStore*.class",
        // Interface with only abstract members plus default-argument synthetic dispatch
        // (`DefaultImpls`); there is no real logic to cover.
        "**/KommonLogger*.class",
    )

/** Minimum line coverage required for the published library modules. */
private val MIN_COVERAGE = BigDecimal("0.70")

/**
 * Wires JaCoCo line-coverage reporting and a verification gate over the module's debug unit
 * tests. The gate is attached to `check`. Call from library modules that ship production code.
 */
internal fun Project.configureJacoco() {
    pluginManager.apply("jacoco")

    // AGP 9 built-in Kotlin compiles to this intermediates directory.
    fun classTree() =
        fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
            exclude(COVERAGE_EXCLUSIONS)
        }

    val execData = layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    val sources = files("src/main/kotlin")

    val report =
        tasks.register<JacocoReport>("jacocoTestReport") {
            dependsOn("testDebugUnitTest")
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
            classDirectories.setFrom(classTree())
            sourceDirectories.setFrom(sources)
            executionData.setFrom(execData)
        }

    tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
        dependsOn(report)
        classDirectories.setFrom(classTree())
        sourceDirectories.setFrom(sources)
        executionData.setFrom(execData)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = MIN_COVERAGE
                }
            }
        }
    }

    tasks.named("check") { dependsOn("jacocoCoverageVerification") }
}
