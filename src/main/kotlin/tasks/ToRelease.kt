package tasks

import dateStr
import devFinishName
import k.common.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import productVer
import versionFile
import java.nio.file.Path
import kotlin.io.path.*

open class ToRelease : DefaultTask() {
    init {
        description = "Plan a new version. Main branch only."

        dependsOn(devFinishName)
    }

    @TaskAction
    fun action() {
        val attempt = Path.of(dateStr).extension.int + (productVer == dateStr).choose(1, 0)
        val newVersion = "${Path.of(dateStr).nameWithoutExtension}.${attempt.str.padStart(4, '0')}"

        versionFile.writeText(newVersion)

        msg("New version $newVersion was generated".n, MsgType.OrangeText)
    }
}