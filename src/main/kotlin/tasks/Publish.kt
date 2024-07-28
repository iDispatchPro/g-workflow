package tasks

import imagesName
import k.common.*
import k.docker.Docker
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import params
import projectName

open class Publish : DefaultTask() {
    init {
        description = "Publishing built images to a local Docker registry."

        dependsOn(imagesName)
    }

    @TaskAction
    fun action() {
        replaceError("Failed to publish images") {
            replaceError("Failed to login [${params.registry}]") {
                Docker.login(params.registry)
            }

            dockerFiles(imagesDir)
                .forEach {
                    imageTags(it.name, projectName).forEach { tag ->
                        replaceError("Failed to push [$tag]") {
                            Docker.push(tag)
                        }

                        msg("""Image "$tag" was published""".n.n, MsgType.OrangeText)
                    }
                }
        }
    }
}