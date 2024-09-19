package tasks

import buildName
import imagesName
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get

const val testGroup = "AfterBuild"
val testsAfterBuildName = "$testName-after-build"

open class TestsAfterBuild : Test()
{
    init
    {
        description = "Running project tests (group = $testGroup) with a production bundle"

        dependsOn(imagesName)

        useTestNG {
            parallel = "methods"
            threadCount = 10
            includeGroups = setOf(testGroup)
        }

        /*useJUnit {
            listOf("koverLog", "koverHtmlReport")
                .forEach {
                    if (project.tasks.findByPath(it) != null)
                        finalizedBy(it)
                }
        }*/

        testClassesDirs = project.extensions.getByType(SourceSetContainer::class.java)["test"].runtimeClasspath
        classpath = testClassesDirs
    }

    override fun getDryRun() =
        project.objects
            .property(Boolean::class.java)
            .also { it.set(false) }
}