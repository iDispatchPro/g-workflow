package tasks

import GLOBAL_PREFIX
import TaskContext
import VERSION_FILE
import k.common.*
import k.git.GitRepo
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import toReleaseName
import java.io.File
import javax.inject.Inject

const val deployDependent = "$GLOBAL_PREFIX-deploy-dependent-libs"
const val gradleFile = "build.gradle.kts"

open class DeployDependent @Inject constructor(private val context: TaskContext) : Jar()
{
    init
    {
        description = "Update dependencies and deploy dependent libraries"
    }

    private fun rule(name : String, group : String = context.extension.groupId.get()) =
        "implementation\\(\\s*?\"${"$group:$name:".maskRegExp}.*?\"\\s*?\\)".toRegex() to "implementation(\"$group:$name:${context.productVersion}\")"

    private fun executeTasks(dir : File, vararg tasks : String)
    {
        val gradle = if (isWindows)
            "gradlew.bat"
        else
            "./gradlew"

        tasks
            .forEach { task ->
                Process("$gradle $task", dir, mapOf("JAVA_HOME" to System.getProperty("java.home")))
                    .wait(10.min, false)
            }
    }

    @TaskAction
    fun action()
    {
        executeTasks(context.projectDir, toReleaseName, deployName)

        val libs = context.extension.dependedLibs.get()

        val implements = (listOf(context.projectName) + libs).map { lib -> rule(lib) }

        libs.isNotEmpty() mustBeSpecified "dependedLibs"

        libs
            .forEach { lib ->
                msg("\nUpdate $lib... ", MsgType.BlueText)

                val libDir = File(context.projectDir.parent, lib).mustBeFound

                File(libDir, VERSION_FILE).writeText(context.productVersion)

                val gradleFile = File(libDir, gradleFile).mustBeFound
                var gradleText = gradleFile
                    .text
                    .replace("id[( ]+\"ru.old-school-geek.g-workflow\"[) ]+version\\w+\"[\\d.]+\"",
                             "id(\"ru.old-school-geek.g-workflow\") version \"${appConfig["ImplementationVersion"]}\"")

                implements
                    .forEach { rule -> gradleText = gradleText.replace(rule.first, rule.second) }

                gradleFile.writeText(gradleText)

                executeTasks(libDir, deployName)

                msg("Done", MsgType.Ok)
            }

        msg("\n\nCommit changes... ", MsgType.BlueText)

        libs
            .forEach { lib ->
                val libDir = File(context.projectDir.parent, lib).mustBeFound

                val repo = GitRepo(libDir)

                repo.commit("Update depends with version ${context.productVersion}")
                repo.tag(context.productVersion)
            }

        msg("Done", MsgType.Ok)

        msg("\n\nUse follow instructions:\n\n", MsgType.OrangeText)
        msg(implements.joinToString("\n") { it.second }.n, MsgType.BlueText)
    }
}