package tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get

open class LibTests : Test()
{
    init
    {
        description = "Running library tests."

        useTestNG {
            parallel = "methods"
            threadCount = 10
        }

        testClassesDirs = project.extensions.getByType(SourceSetContainer::class.java)["test"].runtimeClasspath
        classpath = testClassesDirs
    }

    override fun getDryRun() =
        project.objects.property(Boolean::class.java).also { it.set(false) }
}