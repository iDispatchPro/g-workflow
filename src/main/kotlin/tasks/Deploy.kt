package tasks

import GLOBAL_PREFIX
import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import productVer
import projectName
import publishName
import tasks.version.checkBranchName

val deployName = "$GLOBAL_PREFIX-deploy"

open class Deploy : DefaultTask()
{
    init
    {
        description = "The complete application delivery cycle: clean-build-test-images-publish. Only for the final build from the main branch to production."

        dependsOn(devFinishName, publishName, checkBranchName)
    }

    @TaskAction
    fun action()
    {
        msg("$projectName with version: $productVer was published".n.n, MsgType.OrangeText)
    }
}