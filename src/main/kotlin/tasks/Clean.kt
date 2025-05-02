package tasks

import TaskContext
import k.common.tryProc
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import pluginName
import javax.inject.Inject

open class Clean @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Deleting builds, cleanup Docker, and those built with $pluginName."

        dependsOn("clean"/*, removeImages, removeVolumes, envDownName*/)
    }

    @TaskAction
    fun action()
    {
        println("Remove build ${context.buildDir}...")

        tryProc {
            context.buildDir.deleteRecursively()
        }

        //Docker.cleanUp(1.w)
    }
}