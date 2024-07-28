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
                                 "G-Workflow-Version" to appConfig["ImplementationVersion"]))
        }
    }

    @TaskAction
    fun action() {
        msg("\n$jarName was built\n\n", MsgType.OrangeText)
    }
}