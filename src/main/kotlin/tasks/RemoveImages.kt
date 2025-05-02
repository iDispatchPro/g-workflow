package tasks

import TaskContext
import envDownName
import instancesLabel
import k.common.mute
import k.common.stage
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import pluginName
import javax.inject.Inject

open class RemoveImages @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Deleting built with $pluginName and unnamed local images."

        mustRunAfter(envDownName)
    }

    @TaskAction
    fun action() =
        stage("Remove images", logError = true) {
            docker.snapshot.images
                .filter { instancesLabel in it.labels || it.image.version == "<none>" }
                .forEach {
                    mute {
                        docker.removeImage(it.id)
                    }
                }
        }
}