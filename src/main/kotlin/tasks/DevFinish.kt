package tasks

import TaskContext
import autoAfterEvaluate
import buildName
import checkName
import cleanName
import imagesName
import org.gradle.api.DefaultTask
import javax.inject.Inject

const val resourcesName = "buildResources"

open class DevFinish @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "The full development cycle of an application: cleaning, building, testing, images."

        autoAfterEvaluate {
            if (tasks.names.contains(resourcesName))
            {
                tasks.getByName("processResources").mustRunAfter(resourcesName)

                tasks.getByName(buildName).mustRunAfter(resourcesName)

                dependsOn(resourcesName, checkName)

                tasks.getByName(resourcesName).dependsOn(cleanName)
            }

            dependsOn(cleanName,
                      unitTestsName,
                      integrationTestsName,
                      imagesName)

            project.tasks.getByName("compileKotlin").mustRunAfter(cleanName)
            project.tasks.getByName("compileJava").mustRunAfter(cleanName)
        }
    }
}