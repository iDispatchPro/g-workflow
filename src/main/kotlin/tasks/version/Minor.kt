package tasks.version

open class Minor : Major()
{
    override fun getNewVersion() =
        getVersion().also {
            it.minor++
            it.build = 0
        }
}