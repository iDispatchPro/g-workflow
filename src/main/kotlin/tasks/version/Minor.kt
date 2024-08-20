package tasks.version

import org.gradle.api.tasks.Internal

open class Minor : Major()
{
    @Internal
    override fun getNewVersion() =
        getVersion().also {
            it.minor++
            it.build = 0
        }
}