package tasks.version

import Git
import k.common.orThrow
import k.common.replaceError
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class CheckBranchTask : DefaultTask()
{
    init
    {
        description = "Check status of current branch"
    }

    @TaskAction
    fun action()
    {
        val changes = replaceError("Invalid or non-existent GIT repository.") { Git.changes }

        changes.isBlank() orThrow "The branch ${Git.branch} should be commited and have no issues.\n${Git.status}"
    }
}