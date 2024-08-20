package tasks

import buildName
import fullJarName
import k.common.cmdLine
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class Run : DefaultTask()
{
    init
    {
        description = "Run of jar-file"

        dependsOn(buildName)
    }

    @TaskAction
    fun action() =
        println(cmdLine("${System.getProperty("java.home")}/Bin/java -jar $fullJarName"))
}