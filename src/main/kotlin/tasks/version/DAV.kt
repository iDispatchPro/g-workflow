package tasks.version

import dateStr
import k.common.choose
import k.common.int
import k.common.str
import productVer
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

const val davPartsCount = 4

open class DAV : ReleaseTask() {
    override fun getNewVersion(): Any {
        checkVersionFormat("Date As Version", davPartsCount)

        val attempt = Path.of(dateStr).extension.int + (productVer == dateStr).choose(1, 0)

        return "${Path.of(dateStr).nameWithoutExtension}.${attempt.str.padStart(4, '0')}"
    }
}