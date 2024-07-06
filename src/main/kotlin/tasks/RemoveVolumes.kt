package tasks

import k.docker.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import removeImages

open class RemoveVolumes : DefaultTask()
{
    init
    {
        description = "Removing unnamed volumes in Docker."

        mustRunAfter(removeImages)
    }

    @TaskAction
    fun action() =
        Docker.cleanUpVolumes()
}