package tasks

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

const val DETEKT_CONFIG = "detekt.yaml"

open class Check : DefaultTask() {
    init {
        description = "Code quality check."

        if (File(DETEKT_CONFIG).exists()) {
            project.extensions.getByType(DetektExtension::class.java).apply {
                allRules = true
                parallel = true
                buildUponDefaultConfig = true

                config.setFrom(DETEKT_CONFIG)
            }

            dependsOn(project.tasks.withType(Detekt::class.java))
        }
    }

    @TaskAction
    fun action() {
        if (!File(DETEKT_CONFIG).exists())
            println("$DETEKT_CONFIG doesn't exist")
    }
}