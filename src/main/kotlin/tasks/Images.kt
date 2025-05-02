package tasks

import TaskContext
import buildName
import checkName
import k.common.*
import k.docker.Docker
import k.docker.defaultVersion
import k.docker.models.Image
import k.parallels.parallel
import k.stream.text
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import params
import java.io.File
import javax.inject.Inject

const val dockerFile = "dockerfile"
const val imagesDir = "images"

val docker = Docker()

fun imageTags(context: TaskContext, path: String, name: String, defaultName: String): List<String>
{
    val fixedName = (path and "/") + ((name - "." - dockerFile) or defaultName)

    return params
        .registryUrl
        .list
        .flatMap { registry ->
            listOf(context.productVersion, defaultVersion)
                .map { ver -> Image(registry, fixedName, ver).str }
        }
}

fun buildImage(context: TaskContext, path : String, name : String, defaultName : String, source : String, labels : Map<String, String> = mapOf())
{
    val dockerFile = File(context.buildDir, name)

    context.prepareFile(File(source), dockerFile)

    imageTags(context, path, name, defaultName)
        .forEach { tag ->
            docker.buildImage(dockerFile, tag, labels)

            msg("""Image "$tag" was built""".n, MsgType.Ok)
        }
}

private val TaskContext.defaultDockerFile
    get() = File(buildDir, dockerFile)

fun dockerFiles(context: TaskContext, dir: String) =
    File(context.projectDir, dir).let { root ->
        root
            .files
            .filter { it.name.endsWith(dockerFile, true) } ensure context.defaultDockerFile
    }

fun buildImages(
    context: TaskContext,
    path: String,
    dir: String,
    defaultName: String,
    labels: Map<String, String> = mapOf()
) = replaceError("Failed to build images") {
        val sourceDir = File(dir)

        if (sourceDir.exists())
            sourceDir.copyRecursively(context.buildDir, true)

        context.defaultDockerFile.writeText(resource(dockerFile).text)

        dockerFiles(context, dir) parallel {
            buildImage(context, path, it.name, defaultName, it.str, labels)
        }
    }

open class Images @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Building images for Docker files from the 'images' directory or for automatically generated ones."

        inputs.files(context.fullJarName)

        mustRunAfter(unitTestsName, checkName)
        dependsOn(buildName)
    }

    @TaskAction
    fun action() =
        buildImages(context, params.registryPath, imagesDir, context.projectName)
}