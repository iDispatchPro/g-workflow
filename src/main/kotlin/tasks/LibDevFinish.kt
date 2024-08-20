package tasks

import checkName
import cleanName
import org.gradle.api.DefaultTask
import testName

open class LibDevFinish : DefaultTask()
{
    init
    {
        description = "The full development cycle of an application: cleaning, building, testing."

        dependsOn(cleanName, testName, checkName, "jar")

        project.tasks.getByName("compileKotlin").mustRunAfter(cleanName)
        project.tasks.getByName("compileJava").mustRunAfter(cleanName)
    }
}