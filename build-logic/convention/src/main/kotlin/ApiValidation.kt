import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/** Wires BCV's public tasks to published release classes without relying on Kotlin plugin detection. */
internal fun Project.configureApiValidation() {
    val apiFileName = "$name.api"
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val runtime = configurations.create("apiValidationRuntimeClasspath") {
        isCanBeConsumed = false
        description = "Isolated runtime for Android JVM API validation."
    }
    listOf("asm", "asm-tree", "kotlin-metadata-jvm").forEach { alias ->
        dependencies.add(runtime.name, libs.findLibrary(alias).get())
    }

    val androidComponents = extensions.getByType<LibraryAndroidComponentsExtension>()
    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
        val classes = tasks.register<ExtractApiClassesTask>("extractApiClasses") {
            aar.set(variant.artifacts.get(SingleArtifact.AAR))
            classesJar.set(layout.buildDirectory.file("api/classes.jar"))
        }
        val buildApi = tasks.register<KotlinApiBuildTask>("apiBuild") {
            inputJar.set(classes.flatMap { it.classesJar })
            outputApiFile.set(layout.buildDirectory.file("api/$apiFileName"))
            runtimeClasspath.from(runtime)
            doLast {
                check(outputApiFile.get().asFile.readText().isNotBlank()) {
                    "Android API dump is empty; check the published classes and API validation wiring."
                }
            }
        }
        val baseline = layout.projectDirectory.file("api/$apiFileName")
        val checkApi = tasks.register<KotlinApiCompareTask>("apiCheck") {
            group = "verification"
            description = "Checks the published release JVM API against its committed baseline."
            projectApiFile.set(baseline)
            generatedApiFile.set(buildApi.flatMap { it.outputApiFile })
        }
        val dumpApi = tasks.register<Copy>("apiDump") {
            group = "other"
            description = "Updates the release JVM API baseline for an intentional public API change."
            from(buildApi.flatMap { it.outputApiFile })
            into(layout.projectDirectory.dir("api"))
        }
        checkApi.configure { mustRunAfter(dumpApi) }
        tasks.named("check") { dependsOn(checkApi) }
    }
}
