import java.io.*
import java.security.MessageDigest
import java.util.*

fun computeAndSaveFileHash(file : File, shaAlgorithms : List<String>) =
    shaAlgorithms
        .map { algorithm ->
            val shaSum = HashUtils.getCheckSumFromFile(MessageDigest.getInstance(algorithm), file)

            val shaFile = File("${file.absolutePath}.${algorithm.replace("-", "").lowercase(Locale.getDefault())}")

            shaFile.writeText(shaSum)

            shaFile
        }

object HashUtils {
    const val STREAM_BUFFER_LENGTH = 1024

    fun getCheckSumFromFile(digest : MessageDigest,
                            file : File) : String {
        val fis = FileInputStream(file)
        val byteArray = updateDigest(digest, fis).digest()
        fis.close()
        val hexCode = encodeHex(byteArray, true)

        return String(hexCode)
    }

    private fun updateDigest(digest : MessageDigest,
                             data : InputStream) : MessageDigest {
        val buffer = ByteArray(STREAM_BUFFER_LENGTH)
        var read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)

        while (read > -1) {
            digest.update(buffer, 0, read)
            read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        }

        return digest
    }

    private val DIGITS_LOWER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    private val DIGITS_UPPER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

    fun encodeHex(data : ByteArray,
                  toLowerCase : Boolean) =
        encodeHex(data, if (toLowerCase) DIGITS_LOWER else DIGITS_UPPER)

    fun encodeHex(data : ByteArray,
                  toDigits : CharArray) : CharArray {
        val l = data.size
        val out = CharArray(l shl 1)
        // two characters form the hex value.
        var i = 0
        var j = 0

        while (i < l) {
            out[j++] = toDigits[0xF0 and data[i].toInt() ushr 4]
            out[j++] = toDigits[0x0F and data[i].toInt()]
            i++
        }
        
        return out
    }
}
