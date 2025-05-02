package tasks.version

import TaskContext
import javax.inject.Inject

open class Patch @Inject constructor(context: TaskContext) : Major(context)
{
    override fun getNewVersion() =
        getVersion().also {
            it.build++
        }
}