package tasks.version

import org.gradle.api.tasks.Internal

open class Patch : Major()
{
    @Internal
    override fun getNewVersion() =
        getVersion().also {
            it.build++
        }
}