package tasks.version

import TaskContext
import javax.inject.Inject

open class Minor @Inject constructor(context: TaskContext) : Major(context)
{
    override fun getNewVersion() =
        getVersion().also {
            it.minor++
            it.build = 0
        }
}