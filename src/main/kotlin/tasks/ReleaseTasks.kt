package tasks

import Git
import checkMainBranch
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import versionFile

const val versionPartsCount = 3

class Version(var major : Int,
              var minor : Int,
              var build : Int)
{
    override fun toString() =
        "$major.$minor.$build"
}

abstract class ReleaseTask : DefaultTask()
{
    init
    {
        description = "Bump to next ${className.low - "release" - "_decorated"} version"
    }

    @Internal
    protected fun getVersion() : Version
    {
        val parts = (versionFile.text default "0.0.0")
            .trim()
            .split('.')

        (parts.size == versionPartsCount) orThrow "Invalid format of ${versionFile.name}"

        return Version(parts[0].int, parts[1].int, parts[2].int)
    }

    @TaskAction
    fun action()
    {
        versionFile.writeText(getNewVersion().toString())

        val actualVersion = getVersion().toString()

        Git.commit("release", actualVersion)

        msg("\nVersion was updated to \"$actualVersion\"".n.n, MsgType.OrangeText)
    }

    @Internal
    protected abstract fun getNewVersion() : Version
}

open class ReleaseMajor : ReleaseTask()
{
    @Internal
    override fun getNewVersion() =
        getVersion().also {
            it.major++
            it.minor = 0
            it.build = 0
        }
}

open class ReleaseMinor : ReleaseTask()
{
    @Internal
    override fun getNewVersion() =
        getVersion().also {
            it.minor++
            it.build = 0
        }
}

open class ReleasePatch : ReleaseTask()
{
    @Internal
    override fun getNewVersion() =
        getVersion().also {
            it.build++
        }
}