package tasks

import buildDir
import imagesName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import projectDir
import projectName
import java.io.File

class Compose(val dir : String)
{
    private val filePath = "$dir/compose.yaml"
    private val sourceFile = File("$projectDir/$filePath")
    private val composeFile = File("$buildDir/$filePath")

    private fun doAction(cmd : String)
    {
        if (sourceFile.exists())
        {
            composeFile.parentFile.mkdirs()
            sourceFile.parentFile.copyRecursively(composeFile.parentFile, true)

            prepareFile(sourceFile, composeFile)

            val groupName = "$projectName-$dir".low

            cmdLine("""docker compose --project-name $groupName -f ${composeFile.name} $cmd""", composeFile.parentFile)
        }
    }

    fun start() =
        doAction("up -d --wait")

    fun stop() =
        doAction("down")
}

fun prepareEnv()
{
    if (File(projectDir, "env").exists())
    {
        buildImages("env", "$projectName-test")

        Compose("env").start()
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
}