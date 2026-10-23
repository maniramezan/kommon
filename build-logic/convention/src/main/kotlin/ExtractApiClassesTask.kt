import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/** Extracts the published JVM classes through AGP's public AAR artifact API. */
@CacheableTask
internal abstract class ExtractApiClassesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aar: RegularFileProperty

    @get:OutputFile
    abstract val classesJar: RegularFileProperty

    @TaskAction
    fun extract() {
        ZipFile(aar.get().asFile).use { archive ->
            val entry = requireNotNull(archive.getEntry("classes.jar")) { "Published AAR has no classes.jar" }
            val output = classesJar.get().asFile
            output.parentFile.mkdirs()
            archive.getInputStream(entry).use { input -> output.outputStream().use(input::copyTo) }
        }
    }
}
