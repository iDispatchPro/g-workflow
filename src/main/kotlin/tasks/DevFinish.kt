package tasks

import buildName
import cleanName
import imagesName
import org.gradle.api.DefaultTask
import testName

const val resourcesName = "buildResources"

open class DevFinish : DefaultTask() {
    init {
        description = "The full development cycle of an application: cleaning, building, testing, images."

        project.afterEvaluate {
            if (project.tasks.count { it.name == resourcesName } > 0) {
                project.tasks.getByName("processResources").mustRunAfter(resourcesName)

                project.tasks.getByName(buildName).mustRunAfter(resourcesName)

                dependsOn(resourcesName)

                project.tasks.getByName(resourcesName).dependsOn(cleanName)
            }
        }

        dependsOn(cleanName, testName, imagesName)

        project.tasks.getByName("compileKotlin").mustRunAfter(cleanName)
        project.tasks.getByName("compileJava").mustRunAfter(cleanName)
    }
}