package tasks

import buildDir
import buildName
import checkName
import defaultDockerFile
import fullJarName
import k.common.*
import k.docker.Docker
import k.docker.defaultVersion
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
import java.io.File

const val dockerFile = "dockerfile"
const val imagesDir = "images"

val docker = Docker()

fun imageTags(path : String, name : String, defaultName : String) : List<String>
{
    val fixedName = (path and "/") + ((name - "." - dockerFile) or defaultName)

    return params
        .registryUrl
        .list
        .flatMap { registry -> listOf(productVer, defaultVersion).map { ver -> Image(registry, fixedName, ver).str } }
}

fun buildImage(path : String, name : String, defaultName : String, source : String, labels : Map<String, String> = mapOf())
{
    val dockerFile = File(buildDir, name)

    prepareFile(File(source), dockerFile)

    imageTags(path, name, defaultName)
        .forEach { tag ->
            docker.buildImage(dockerFile, tag, labels)

            msg("""Image "$tag" was built""".n, MsgType.Ok)
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

fun buildImages(path : String, dir : String, defaultName : String, labels : Map<String, String> = mapOf()) =
    replaceError("Failed to build images") {
        val sourceDir = File(dir)

        if (sourceDir.exists())
            sourceDir.copyRecursively(File(buildDir), true)

        defaultDockerFile.writeText(resource(dockerFile).text)

        dockerFiles(dir) parallel {
            buildImage(path, it.name, defaultName, it.str, labels)
        }
    }

open class Images : DefaultTask()
{
    init
    {
        description = "Building images for Docker files from the 'images' directory or for automatically generated ones."

        inputs.files(fullJarName)

        mustRunAfter(unitTestsName, checkName)
        dependsOn(buildName)
    }

    @TaskAction
    fun action() =
        buildImages(params.registryPath, imagesDir, projectName)
}