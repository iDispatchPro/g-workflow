package tasks

import findMainClass
import fullJarName
import jarName
import k.common.*
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import productVer
import java.io.File

open class Build : Jar() {
    init {
        description = "Build a jar with all dependencies and the correct version."

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveFileName.set(fullJarName)

        if (File("src/main").exists())
            inputs.dir("src/main") // Костыль для корректного определение UP-TO-DATE

        dependsOn("compileKotlin", "compileJava", "processResources")

        manifest {
            attributes(hashMapOf("Main-Class" to findMainClass(),
                                 "Implementation-Title" to project.name,
                                 "Implementation-Version" to productVer,
                                 "Jam-Version" to appConfig["ImplementationVersion"]))
        }
    }

    @TaskAction
    fun action() {
        injectDepends()

        msg("\n$jarName was built\n\n", MsgType.OrangeText)
    }

    private val depends = File(project.projectDir, "depends")

    inner class Depend(val group : String,
                       val srcPath : String,
                       val id : String,
                       val version : String) {
        val dir
            get() = File(depends, "${group.replace(".", "/")}/$id/$version")
    }

    private fun injectDepends() {
        if (!depends.exists())
            return

        println("Update injected depends")

        val cacheDir = File(project.gradle.gradleUserHomeDir.path, "/caches/modules-2/files-2.1/")

        val libraries = project.configurations
            .flatMap { it.dependencies }
            .distinctBy { it.toString() }
            .filter { (it.group?.lowercase() ?: "") in listOf("ru", "k", "wb") }
            .map {
                Depend(it.group.str,
                       "${it.group}/${it.name}",
                       it.name.str,
                       it.version.str)
            } + Depend("ru.wildberries.jam",
                       "ru.wildberries/Jam",
                       "ru.wildberries.jam.gradle.plugin",
                       Build::class.java.`package`.implementationVersion)

        libraries
            .forEach { lib ->
                lib.dir.force()

                val srcDir = File(cacheDir, "${lib.srcPath}/${lib.version}")
                val files = srcDir
                    .walk()
                    .filter { it.extension.lowercase() in listOf("pom", "jar") }

                files
                    .forEach { source ->
                        val target = File(lib.dir, "${lib.id}-${lib.version}${source.name.substringAfterLast(lib.version)}")

                        if (!target.exists()) {
                            if (source.extension.low == "jar")
                                source.copyTo(target)
                            else
                                target.writeText(source
                                                     .readText()
                                                     .replaceFirst(Regex("<groupId>.+?</groupId>"), "<groupId>${lib.group}</groupId>")
                                                     .replaceFirst(Regex("<artifactId>.+?</artifactId>"), "<artifactId>${lib.id}</artifactId>")
                                                )
                        }

                        println("Add ${target.name}")
                    }

                lib.dir.files.any { it.extension.low == "pom" } orThrow "$group:${lib.id} has no POM file. Try to clean gradle cache and repeat."
            }

        depends
            .walk()
            .filter { it.isDirectory && it.dirs.isEmpty() }
            .filter { it.absolutePath !in libraries.map { it.dir.absolutePath } }
            .forEach {
                if (!it.deleteRecursively())
                    error("Failed to clean depends directory. Please restart application, then try again.")

                println("Remove ${it.absolutePath}")
            }
    }
}