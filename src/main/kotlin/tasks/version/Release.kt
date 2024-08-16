package tasks.version

import Git
import checkBranchName
import devFinishName
import k.common.MsgType
import k.common.className
import k.common.low
import k.common.minus
import k.common.msg
import k.common.mustBeSpecified
import k.common.n
import k.common.orThrow
import k.common.str
import k.common.text
import k.common.title
import k.common.tryProc
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import versionFile
import java.io.File

abstract class ReleaseTask : DefaultTask() {
    init {
        description = "Update to next $id version"

        dependsOn(devFinishName, checkBranchName)
    }

    private val id
        get() = className.low - "release" - "_decorated"

    @TaskAction
    fun action() {
        versionFile.writeText(getNewVersion().str)

        val newVersion = versionFile.text mustBeSpecified "Version in $versionFile"

        tryProc {
            File(".kotlin").deleteRecursively()
        }

        Git.commit("Update version to $newVersion")
        Git.tag(newVersion)

        msg("New $id version $newVersion was created".n, MsgType.OrangeText)
    }

    @Internal
    protected abstract fun getNewVersion(): Any

    @Internal
    fun checkVersionFormat(format : String, partsCount: Int) =
        ((versionFile.text?.split('.')?.size ?: partsCount) == partsCount) orThrow "Incompatible version format ($format). To change it, delete the file version.txt."
}