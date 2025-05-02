package tasks

import TaskContext
import k.common.MsgType
import k.common.appConfig
import k.common.msg
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import java.io.File
import javax.inject.Inject

open class Build @Inject constructor(private val context: TaskContext) : Jar()
{
    init
    {
        description = "Build a jar with all dependencies and the correct version."

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveFileName.set(context.fullJarName)

        if (File("src/main").exists())
            inputs.dir("src/main") // Костыль для корректного определение UP-TO-DATE

        dependsOn("compileKotlin", "compileJava", "processResources")

        manifest {
            attributes(hashMapOf("Main-Class" to context.mainClass,
                                 "Implementation-Title" to context.projectName,
                                 "Implementation-Version" to context.productVersion,
                                 "G-Workflow-Version" to appConfig["ImplementationVersion"]))
        }

        val sources = project.extensions.getByType(SourceSetContainer::class.java)["main"]

        from(sources.output + sources.runtimeClasspath.filter { it.exists() }.map { if (it.isDirectory) it else project.zipTree(it) })
    }

    @TaskAction
    fun action()
    {
        msg("\n${context.jarName} was built\n", MsgType.Ok)
    }
}