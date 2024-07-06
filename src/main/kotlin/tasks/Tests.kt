package tasks

import buildName
import envUpName
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.configurationcache.problems.*
import org.gradle.kotlin.dsl.*

open class Tests : Test()
{
    init
    {
        description = "Running project tests with a pre-created test environment ($envUpName)."

        dependsOn(buildName)

        useTestNG {
            parallel = "methods"
            threadCount = 10
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

    @TaskAction
    fun action()
    {
        // prepareEnv()

        //removeEnv()
    }

    override fun getDryRun() =
        project.objects.property(Boolean::class.java).also { it.set(false) }
}