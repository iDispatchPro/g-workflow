package tasks

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

open class DeployLib @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "The complete library delivery cycle: clean-build-test-publish. Only for the final build from the main branch to production."

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