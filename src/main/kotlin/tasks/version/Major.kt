package tasks.version

import k.common.default
import k.common.int
import k.common.or
import k.common.text
import org.gradle.api.tasks.Internal
import versionFile

open class Major : ReleaseTask()
{
    @Internal
    override fun getNewVersion() =
        getVersion().also {
            it.major++
            it.minor = 0
            it.build = 0
        }

    @Internal
    fun getVersion() : Version
    {
        checkVersionFormat("Major.Minor.Patch", versionPartsCount)

        val parts = (versionFile.text or "0.0.0")
            .trim()
            .split('.')

        return Version(parts[0].int, parts[1].int, parts[2].int)
    }
}