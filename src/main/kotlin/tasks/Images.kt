package tasks

import buildDir
import buildName
import checkName
import defaultDockerFile
import fullJarName
import k.common.*
import k.docker.Docker
import k.docker.models.Image
import k.parallels.parallel
import k.stream.text
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import params
import patched
import productVer
import projectDir
import projectName
import testName
import java.io.File

const val dockerFile = "dockerfile"
const val imagesDir = "images"

fun imageTags(name : String, defaultName : String) : List<String>
{
    val fixedName = (name - "." - dockerFile) default defaultName

    return params.registryUrl
        .list
        .map { Image(it, fixedName, productVer).fullName }
}

fun buildImage(name : String, defaultName : String, source : String, label : String? = null)
{
    val dockerFile = File(buildDir, name)

    prepareFile(File(source), dockerFile)

    imageTags(name, defaultName).forEach { tag ->
        Docker.buildImage(dockerFile, tag, label)

        msg("""Image "$tag" was built""".n.n, MsgType.OrangeText)
    }
}

fun prepareFile(from : File, to : File)
{
    to.parentFile.mkdirs()
    to.writeText(from.readText().patched)
}

fun dockerFiles(dir : String) =
    File(projectDir, dir).let { root ->
        root
            .files
            .filter { it.name.endsWith(dockerFile, true) } ensure defaultDockerFile
    }

fun buildImages(dir : String, defaultName : String, label : String? = null) =
    replaceError("Failed to build images") {
        replaceError("Failed to login [${params.registry}]") {
            Docker.login(params.registry)
        }

        val sourceDir = File(dir)

        if (sourceDir.exists())
            sourceDir.copyRecursively(File(buildDir), true)

        defaultDockerFile.writeText(resource(dockerFile).text)

        dockerFiles(dir) parallel {
            buildImage(it.name, defaultName, it.str, label)
        }
    }

open class Images : DefaultTask()
{
    init
    {
        description = "Building images for Docker files from the 'images' directory or for automatically generated ones."

        inputs.files(fullJarName)

        mustRunAfter(testName, checkName)
        dependsOn(buildName)
    }

    @TaskAction
    fun action() =
        buildImages(imagesDir, projectName)
}