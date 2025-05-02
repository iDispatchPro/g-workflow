package tasks

import TaskContext
import imagesName
import k.common.MsgType
import k.common.msg
import k.common.n
import k.common.replaceError
import k.docker.models.image
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import params
import javax.inject.Inject

open class Publish @Inject constructor(private val context: TaskContext) : DefaultTask()
{
    init
    {
        description = "Publishing built images to a local Docker registry."

        dependsOn(imagesName)
    }

    @TaskAction
    fun action()
    {
        replaceError("Failed to publish images") {
            dockerFiles(context, imagesDir)
                .forEach {
                    imageTags(context, params.registryPath, it.name, context.projectName)
                        .forEach { tag ->
                            replaceError("Failed to push [$tag]") {
                                docker.push(tag.image, params.registry)
                            }

                            msg("""Image "$tag" was published""".n.n, MsgType.Ok)
                        }
                }
        }
    }
}