package tasks

import GLOBAL_PREFIX
import TaskContext
import autoAfterEvaluate
import devFinishName
import k.common.MsgType
import k.common.msg
import k.common.n
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import publishName
import javax.inject.Inject

const val deployName = "$GLOBAL_PREFIX-deploy"

open class Deploy @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "The complete application delivery cycle: clean-build-test-images-publish. Only for the final build from the main branch to production."

        autoAfterEvaluate {
            dependsOn(devFinishName, publishName, checkBranchName)
        }
    }

    @TaskAction
    fun action()
    {
        msg("${context.projectName} with version: ${context.productVersion} was published".n.n, MsgType.Ok)
    }
}