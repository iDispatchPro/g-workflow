import k.common.*
import k.stream.text
import java.io.File

object Git {
    fun commit(name : String, version : String) {
        cmdLine("git add --all")
        cmdLine("git commit -m 'chore($name): $version'")

        tag(version)
    }

    fun getTag() =
        cmdLine("git describe --tags --abbrev=0")

    fun tag(version : String) {
        val commitHash = cmdLine("git rev-parse --short HEAD")

        cmdLine("git tag -a v$version $commitHash -m v$version")
    }

    fun getBranch() =
        try {
            val branch = cmdLine("git rev-parse --abbrev-ref HEAD")

            if (branch same "HEAD")
                ""
            else
                branch
        }
        catch (_ : Throwable) {
            ""
        }

    fun installHooks() {
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