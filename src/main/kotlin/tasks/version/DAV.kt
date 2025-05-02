package tasks.version

import TaskContext
import k.common.choose
import k.common.int
import k.common.str
import k.common.text
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

const val davPartsCount = 4

open class DAV @Inject constructor(context: TaskContext) : ReleaseTask(context)
{
    override fun getNewVersion() : Any
    {
        if (context.versionFile.text.isNotBlank())
            checkVersionFormat("Date As Version", davPartsCount)

        val attempt = Path.of(context.dateStr).extension.int + (context.productVersion == context.dateStr).choose(1, 0)

        return "${Path.of(context.dateStr).nameWithoutExtension}.${attempt.str.padStart(4, '0')}"
    }
}