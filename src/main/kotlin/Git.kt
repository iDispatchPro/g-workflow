import k.common.cmdLine
import k.common.mustBeSpecified
import k.common.resource
import k.common.same
import k.common.text
import k.stream.text
import java.io.File

object Git
{
    fun commit(message : String, dir : File? = null)
    {
        cmdLine("git add --all", dir)
        cmdLine("git commit -m '$message'", dir)
    }

    val changes
        get() = cmdLine("git status -s")

    val status
        get() = cmdLine("git status")

    fun getTag() =
        cmdLine("git describe --tags --abbrev=0")

    fun tag(version : String, dir : File? = null)
    {
        val commitHash = cmdLine("git rev-parse --short HEAD", dir)

        cmdLine("git tag -a v$version $commitHash -m v$version", dir)
    }

    val branch
        get() = try
        {
            val branch = cmdLine("git rev-parse --abbrev-ref HEAD")

            if (branch same "HEAD")
                ""
            else
                branch
        }
        catch (_ : Throwable)
        {
            ""
        }

    fun installHooks()
    {
        val hooksDir = File(projectDir, ".git/hooks")

        if (hooksDir.exists())
            listOf("hooks/pre-push").forEach {
                val source = resource(it.trim('/'))
                val hookName = File(it).name
                val hookFile = File(hooksDir, hookName)
                val sourceText = source.text mustBeSpecified "Hook content ($hookName)"

                if (hookFile.text != sourceText)
                    hookFile.writeText(sourceText)
            }
    }
}