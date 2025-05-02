package tasks

import TaskContext
import buildName
import k.common.cmdLine
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class Run @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Run of jar-file"

        dependsOn(buildName)
    }

    @TaskAction
    fun action() {
        println("\njar output:\n")

        println(cmdLine("${System.getProperty("java.home")}/Bin/java -jar ${context.fullJarName}"))
    }
}