package tasks.version

const val versionPartsCount = 3

class Version(var major : Int,
              var minor : Int,
              var build : Int)
{
    override fun toString() =
        "$major.$minor.$build"
}