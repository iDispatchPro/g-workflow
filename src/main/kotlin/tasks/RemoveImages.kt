package tasks

import envDownName
import instancesLabel
import k.common.*
import k.docker.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import pluginName

open class RemoveImages : DefaultTask() {
    init {
        description = "Deleting built with $pluginName and unnamed local images."

        mustRunAfter(envDownName)
    }

    @TaskAction
    fun action() =
        stage("Remove images", logError = true) {
            Snapshot().images
                .filter { instancesLabel in it.labels || it.image.version == "<none>" }
                .forEach {
                    mute {
                        Docker.removeImage(it.id)
                    }
                }
        }
}