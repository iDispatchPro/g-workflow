package tasks

import buildDir
import k.common.tryProc
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import pluginName
import java.io.File

open class Clean : DefaultTask()
{
    init
    {
        description = "Deleting builds, cleanup Docker, and those built with $pluginName."

        dependsOn("clean"/*, removeImages, removeVolumes, envDownName*/)
    }

    @TaskAction
    fun action()
    {
        println("Remove build $buildDir...")

        tryProc {
            File(buildDir).deleteRecursively()
        }

        //Docker.cleanUp(1.w)
    }
}