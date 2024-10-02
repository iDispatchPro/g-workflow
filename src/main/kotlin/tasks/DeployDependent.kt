package tasks

import GLOBAL_PREFIX
import VERSION_FILE
import extension
import k.common.*
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import productVer
import projectName
import toReleaseName
import versionFile
import java.io.File

const val deployDependent = "$GLOBAL_PREFIX-deploy-dependent-libs"
const val gradleFile = "build.gradle.kts"

open class DeployDependent : Jar()
{
    init
    {
        description = "Update dependencies and deploy dependent libraries"
    }

    private fun rule(name : String, group : String = extension.groupId.get()) =
        "implementation\\(\\s*?\"${"$group:$name:".maskRegExp}.*?\"\\s*?\\)".toRegex() to "implementation(\"$group:$name:$productVer\")"

    private fun executeTasks(dir : File, vararg tasks : String)
    {
        val gradle = if (isWindows)
            "gradlew.bat"
        else
            "./gradlew"

        tasks
            .forEach { task ->
                Process("$gradle $task", dir, mapOf("JAVA_HOME" to System.getProperty("java.home"))).wait(10.min, false)
            }
    }

    @TaskAction
    fun action()
    {
        executeTasks(project.projectDir, toReleaseName, deployName)

        productVer = versionFile.text.trim()

        val libs = extension.dependedLibs.get()

        val implements = (listOf(projectName) + libs).map { lib -> rule(lib) }

        libs.isNotEmpty() mustBeSpecified "dependedLibs"

        libs
            .forEach { lib ->
                msg("\nUpdate $lib... ", MsgType.BlueText)

                val libDir = File(project.projectDir.parent, lib).mustBeFound

                File(libDir, VERSION_FILE).writeText(productVer)

                val gradleFile = File(libDir, gradleFile).mustBeFound
                var gradleText = gradleFile.text

                implements
                    .forEach { rule -> gradleText = gradleText.replace(rule.first, rule.second) }

                gradleFile.writeText(gradleText)

                System.getProperty("java.home")

                executeTasks(libDir, deployName)

                msg("Done", MsgType.Ok)
            }

        msg("\n\nCommit changes... ", MsgType.BlueText)

        libs
            .forEach { lib ->
                val libDir = File(project.projectDir.parent, lib).mustBeFound

                Git.commit("Update depends with version $productVer", libDir)
                Git.tag(productVer, libDir)
            }

        msg("Done", MsgType.Ok)

        msg("\n\nUse follow instructions:\n\n", MsgType.OrangeText)
        msg(implements.joinToString("\n") { it.second }.n, MsgType.BlueText)
    }
}