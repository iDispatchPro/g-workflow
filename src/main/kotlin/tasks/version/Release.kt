package tasks.version

import Git
import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import productVer
import versionFile
import java.io.File

abstract class ReleaseTask : DefaultTask()
{
    init
    {
        description = "Update to next $id version"

        dependsOn(devFinishName)
    }

    private val id
        get() = className.low - "release" - "_decorated"

    @TaskAction
    fun action()
    {
        versionFile.writeText(getNewVersion().str)

        productVer = versionFile.text mustBeSpecified "Version in $versionFile"

        tryProc {
            File(".kotlin").deleteRecursively()
        }

        Git.commit("Update version to $productVer")
        Git.tag(productVer)

        msg("New $id version $productVer was created".n, MsgType.OrangeText)
    }

    @Input
    protected abstract fun getNewVersion() : Any

    fun checkVersionFormat(format : String, partsCount : Int) =
        (versionFile.text.split('.').size == partsCount) orThrow "Incompatible version format ($format). To change it, delete the file version.txt."
}