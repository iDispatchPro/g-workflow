package tasks.version

import TaskContext
import k.common.int
import k.common.or
import k.common.text
import org.gradle.api.tasks.Input
import javax.inject.Inject

open class Major @Inject constructor(context: TaskContext) : ReleaseTask(context)
{
    override fun getNewVersion() =
        getVersion().also {
            it.major++
            it.minor = 0
            it.build = 0
        }

    @Input
    fun getVersion() : Version
    {
        if (context.versionFile.text.isNotBlank())
            checkVersionFormat("Major.Minor.Patch", versionPartsCount)

        val parts = (context.versionFile.text or "0.0.0")
            .trim()
            .split('.')

        return Version(parts[0].int, parts[1].int, parts[2].int)
    }
}