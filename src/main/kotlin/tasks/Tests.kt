package tasks

import GLOBAL_PREFIX
import TaskContext
import buildName
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import javax.inject.Inject

const val unitTestsName = "$GLOBAL_PREFIX-test-unit"

open class Tests @Inject constructor(private val context: TaskContext) : Test()
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

    @Suppress("UnstableApiUsage")
    override fun getDryRun() =
        project.objects
            .property(Boolean::class.java)
            .also { it.set(false) }
}