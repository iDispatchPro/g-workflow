import Extension.Companion.toExtension
import k.common.*
import k.docker.models.Image
import k.git.GitRepo
import org.gradle.api.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension

class TaskContext (
    project: Project
) {
    val extension = project.toExtension(project.objects)
    val projectDir = project.projectDir
    val repo = GitRepo(projectDir)
    val projectName = project.name
    val buildDir = project.layout.buildDirectory.get().asFile
    val versionFile = File(projectDir, VERSION_FILE)

    val dateStr: String = SimpleDateFormat("yy.M.d.HHmm").format(Date())

    val productVersion: String = if (versionFile.exists())
        versionFile.text.trim()
    else
        dateStr

    val jarName = "${project.name.lowercase()}.$productVersion.jar"
    val fullJarName = "$buildDir/$jarName"
    val isMainBranch = !repo.exists || repo.branch in listOf("main", "master", "prod")

    fun prepareFile(from: File, to: File) {
        to.parentFile.mkdirs()

        to.writeText(
            from
                .text
                .replace("j-a-r", jarName)
                .replace("v-e-r-s-i-o-n", productVersion)
                .replace("a-c-c-o-u-n-t", params.registryUrl.str)
                .replace("m-a-i-n--i-m-a-g-e", Image(params.registryUrl, projectName, productVersion).toString())
        )
    }

    fun checkMainBranch() =
        isMainBranch orThrow "The task can only be run in the Main branch"

    val mainFileNames = listOf("main.kt", "app.kt", "application.kt", "main.java", "app.java", "application.java")

    val mainFiles: List<Path> by lazy {
        Files.walk(Path.of("$projectDir/src"))
            .filter { !it.isDirectory() && it.fileName.str.low in mainFileNames }
            .filter { File(it.str).readText().contains("\\s+main\\s*?\\(".toRegex(RegexOption.IGNORE_CASE)) }
            .toList()
    }

    val mainClass by lazy {
        mainFiles.isNotEmpty() orThrow "The file with the entry point was not found ($mainFileNames)"
        (mainFiles.size == 1) orThrow "The file with the entry point can only be 1. Found: ${mainFiles.map { it.fileName }}"

        val mainFile = mainFiles.first()
        val packageStr = File(mainFile.str).readText() extract "package\\s+([\\w.]+)"

        "${packageStr and "."}${mainFile.nameWithoutExtension}Kt"
    }

    val isLib by lazy {
        mainFiles.isEmpty()
    }
}