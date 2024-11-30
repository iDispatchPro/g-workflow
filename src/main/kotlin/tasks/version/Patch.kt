package tasks.version

open class Patch : Major()
{
    override fun getNewVersion() =
        getVersion().also {
            it.build++
        }
}