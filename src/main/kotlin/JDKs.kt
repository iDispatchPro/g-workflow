import k.common.dirs
import k.common.mustBeFound
import k.common.orThrow
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private fun findJdkTableXml() : File
{
    val os = System.getProperty("os.name").lowercase()

    val configDir = when
    {
        os.contains("win") -> File(System.getenv("APPDATA"))
        os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
        else               -> File(System.getProperty("user.home"), ".config")
    }

    val ideaDirs = File(configDir, "JetBrains")
        .dirs
        .filter { it.name.startsWith("IntelliJIdea") }

    ideaDirs.isNotEmpty() orThrow "Failed to find IDE settings"

    (ideaDirs.size == 1) orThrow "Several installed IDE not supported [${ideaDirs.joinToString { it.name }}]"

    return File(ideaDirs.single(), "options/jdk.table.xml") mustBeFound "jdk.table.xml"
}

private fun parseJdkTableXml(file : File) : List<JdkInfo>
{
    val jdks = mutableListOf<JdkInfo>()

    val doc = DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(file)

    doc.documentElement.normalize()

    val jdkElements = doc.getElementsByTagName("jdk")

    for (i in 0 until jdkElements.length)
    {
        val jdkNode = jdkElements.item(i)

        val nameElement = jdkNode
            .childNodes
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) }
                    .find { it.nodeName == "name" }
            }

        val homePathElement = jdkNode
            .childNodes
            .let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) }
                    .find { it.nodeName == "homePath" }
            }

        val name = nameElement?.attributes?.getNamedItem("value")?.nodeValue ?: continue
        val path = homePathElement?.attributes?.getNamedItem("value")?.nodeValue ?: continue

        jdks.add(JdkInfo(name, path))
    }

    return jdks
}

data class JdkInfo(val name : String, val homePath : String)

fun enumJDK() = parseJdkTableXml(findJdkTableXml())