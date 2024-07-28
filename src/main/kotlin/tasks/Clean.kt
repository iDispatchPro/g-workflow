package tasks

import buildDir
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import pluginName
import java.io.File

open class Clean : DefaultTask() {
    init {
        description = "Deleting builds, unnamed volumes, unnamed local images, and those built with $pluginName."

        dependsOn("clean"/*, removeImages, removeVolumes, envDownName*/)
    }

    @TaskAction
    fun action() =
        File(buildDir).deleteRecursively()
}