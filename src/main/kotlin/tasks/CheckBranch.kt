package tasks

import GLOBAL_PREFIX
import TaskContext
import k.common.orThrow
import k.common.replaceError
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

val checkBranchName = "$GLOBAL_PREFIX-check-branch"

open class CheckBranchTask @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Check status of current branch"
    }

    @TaskAction
    fun action()
    {
        val changes = replaceError("Invalid or non-existent GIT repository.") { context.repo.diff }

        changes.isEmpty() || !context.isMainBranch orThrow "The branch ${context.repo.branch} should be commited and have no issues.\n${context.repo.status}"
    }
}