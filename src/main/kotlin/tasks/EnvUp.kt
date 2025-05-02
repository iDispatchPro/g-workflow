package tasks

/*
class Compose(val context: TaskContext, val dir: String)
{
    private val filePath = "$dir/compose.yaml"
    private val sourceFile = File("${context.projectDir}/$filePath")
    private val composeFile = File(context.buildDir, filePath)

    private fun doAction(cmd : String)
    {
        if (sourceFile.exists())
        {
            composeFile.parentFile.mkdirs()
            sourceFile.parentFile.copyRecursively(composeFile.parentFile, true)

            context.prepareFile(sourceFile, composeFile)

            val groupName = "${context.projectName}-$dir".low

            cmdLine("""docker compose --project-name $groupName -f ${composeFile.name} $cmd""", composeFile.parentFile)
        }
    }

    fun start() =
        doAction("up -d --wait")

    fun stop() =
        doAction("down")
}

val hasEnv
    get() = File(projectDir, envDir).exists()

fun prepareEnv()
{
    if (hasEnv)
    {
        buildImages("", envDir, "$projectName-test")

        Compose(envDir).start()
    }
}

open class PrepareEnv : DefaultTask()
{
    init
    {
        description = "Building images for Docker files and running the compose.yaml from the Env directory."

        dependsOn(imagesName)
    }

    @TaskAction
    fun action() =
        prepareEnv()
}*/