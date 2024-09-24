import k.common.*
import java.io.File
import java.nio.file.*
import kotlin.io.path.*

val mainFileNames = listOf("main.kt", "app.kt", "application.kt", "main.java", "app.java", "application.java")

val mainFiles : MutableList<Path> by lazy {
    Files.walk(Path.of("$projectDir/src"))
        .filter { !it.isDirectory() && it.fileName.str.low in mainFileNames }
        .filter { File(it.str).readText().contains("\\s+main\\s*?\\(".toRegex(RegexOption.IGNORE_CASE)) }
        .toList()
}

fun findMainClass() : String
{
    mainFiles.isNotEmpty() orThrow "The file with the entry point was not found ($mainFileNames)"
    (mainFiles.size == 1) orThrow "The file with the entry point can only be 1. Found: ${mainFiles.map { it.fileName }}"

    val mainFile = mainFiles.first()
    val packageStr = File(mainFile.str).readText() extract "package\\s+([\\w.]+)"

    return "${packageStr and "."}${mainFile.nameWithoutExtension}Kt"
}
