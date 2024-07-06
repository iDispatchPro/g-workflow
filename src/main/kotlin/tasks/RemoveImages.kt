package tasks

import jamInstancesLabel
import envDownName
import k.common.*
import k.docker.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class RemoveImages : DefaultTask()
{
    init
    {
        description = "Deleting built with Jam and unnamed local images."

        mustRunAfter(envDownName)
    }

    @TaskAction
    fun action() =
        stage("Remove images", logError = true) {
            Snapshot().images
                .filter { jamInstancesLabel in it.labels || it.image.version == "<none>" }
                .forEach {
                    muteExceptions {
                        Docker.removeImage(it.id)
                    }
                }
        }
}