package tasks

import TaskContext
import checkName
import cleanName
import org.gradle.api.DefaultTask
import javax.inject.Inject

open class LibDevFinish @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "The full development cycle of an application: cleaning, building, testing."

        dependsOn(cleanName, unitTestsName, checkName, "jar")

        project.tasks.getByName("compileKotlin").mustRunAfter(cleanName)
        project.tasks.getByName("compileJava").mustRunAfter(cleanName)
    }
}