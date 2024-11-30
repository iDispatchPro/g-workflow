package tasks

import GLOBAL_PREFIX
import buildName
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get

val testName = "$GLOBAL_PREFIX-test"

open class Tests : Test()
{
    init
    {
        description = "Running project tests (group != $testGroup)"

        dependsOn(buildName)

        useTestNG {
            parallel = "classes"
            threadCount = 10
            excludeGroups = setOf(testGroup)
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