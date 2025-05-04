import k.common.appConfig
import org.testng.annotations.Test

@Test
class Utils
{
    @Test
    fun test()
    {
        val x = "id(\"ru.old-school-geek.g-workflow\") version \"25.2.15.5555\""
            .replace(
                "id[( ]+\"ru.old-school-geek.g-workflow\"[) ]+version\\w+\"[\\d.]+\"".toRegex(),
                "id(\"ru.old-school-geek.g-workflow\") version \"${appConfig["ImplementationVersion"]}\""
                    )

        println(x)
    }

    @Test
    fun jdks()
    {
        enumJDK()
            .forEach {
                println(it)
            }
    }
}