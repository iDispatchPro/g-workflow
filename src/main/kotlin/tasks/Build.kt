package tasks

import TaskContext
import k.common.*
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import java.io.File
import javax.inject.Inject

abstract class Build @Inject constructor(private val context : TaskContext) : Jar()
{
    init
    {
        description = "Build a jar with all dependencies and the correct version."

        archiveFileName.convention(context.fullJarName)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // UP-TO-DATE workaround
        if (File("src/main").exists())
        {
            inputs.dir("src/main")
        }

        dependsOn("compileKotlin", "compileJava", "processResources")

        manifest.attributes(mapOf("Main-Class" to context.mainClass,
                                  "Implementation-Title" to context.projectName,
                                  "Implementation-Version" to context.productVersion,
                                  "G-Workflow-Version" to appConfig["ImplementationVersion"])
                           )

        val sources = project.extensions.getByType(SourceSetContainer::class.java)["main"]
        val runtimeClasspath = sources.runtimeClasspath

        from(sources.output,
             runtimeClasspath.filter { it.exists() }.map {
                 if (it.isDirectory) it else project.zipTree(it)
             }
            )
    }

    @TaskAction
    fun action()
    {
        msg("\n${context.jarName} was built\n", MsgType.Ok)
    }
}
