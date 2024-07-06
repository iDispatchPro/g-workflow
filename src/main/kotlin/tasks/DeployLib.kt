package tasks

import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import productVer
import projectName

open class DeployLib : DefaultTask()
{
    init
    {
        description = "Publish a library with correct version."

        val publishStdName = "publish${projectName.title}PublicationToMavenRepository"

        project.tasks.getByName(publishStdName).mustRunAfter(devFinishName)

        dependsOn(publishStdName, devFinishName)
    }

    @TaskAction
    fun action()
    {
        msg("Current $projectName version: $productVer".n.n, MsgType.BlueText)
        msg("Please use this line for importing library:".n, MsgType.BlueText)
        msg("""implementation("${project.group}:$projectName:$productVer")""".n, MsgType.OrangeText)
    }
}