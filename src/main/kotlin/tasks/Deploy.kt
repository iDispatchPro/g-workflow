package tasks

import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import productVer
import projectName
import publishName

open class Deploy : DefaultTask()
{
    init
    {
        description = "The complete application delivery cycle: clean-build-test-images-publish. Only for the final build from the main branch to production."

        dependsOn(devFinishName, publishName)
    }

    @TaskAction
    fun action()
    {
        msg("$projectName with version: $productVer was published".n.n, MsgType.OrangeText)
    }
}