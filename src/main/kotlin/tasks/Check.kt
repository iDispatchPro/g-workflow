package tasks

import buildDir
import k.common.resource
import k.stream.text
import org.gradle.api.DefaultTask
import java.io.File

const val DETEKT_CONFIG = "detekt.yaml"

open class Check : DefaultTask() {
    init {
        description = "Code quality check."

        project.tasks.getByName("detekt").doFirst {
            File(buildDir, DETEKT_CONFIG)
                .writeText(resource(DETEKT_CONFIG).text)
        }

        dependsOn("detekt")
    }
}