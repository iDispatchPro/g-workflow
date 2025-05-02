package tasks.version

import TaskContext
import autoAfterEvaluate
import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ReleaseTask(protected val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Update to next $id version"

        autoAfterEvaluate {
            dependsOn(devFinishName)
        }
    }

    private val id
        get() = className.low - "release" - "_decorated"

    @TaskAction
    fun action()
    {
        context.versionFile.writeText(getNewVersion().str)

        tryProc {
            File(".kotlin").deleteRecursively()
        }

        if (context.repo.exists) {
            context.repo.commit("Update version to ${context.productVersion}")
            context.repo.tag(context.productVersion)
        }

        msg("New $id version ${context.productVersion} was created".n, MsgType.Ok)
    }

    @Input
    protected abstract fun getNewVersion() : Any

    fun checkVersionFormat(format : String, partsCount : Int) =
        (context.versionFile.text.split('.').size == partsCount)
            .orThrow("Incompatible version format ($format). To change it, delete or modify the file version.txt.")
}