package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import projectDir
import java.io.File

fun removeEnv() {
    if (File(projectDir, "env").exists())
        Compose("env").stop()
}

open class ShutdownEnv : DefaultTask() {
    init {
        description = "Stopping a group of containers for compose.yaml from the Env directory."

        mustRunAfter("clean")
    }

    @TaskAction
    fun action() =
        removeEnv()
}